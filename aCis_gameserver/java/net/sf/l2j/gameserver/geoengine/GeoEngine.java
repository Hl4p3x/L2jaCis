package net.sf.l2j.gameserver.geoengine;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import net.sf.l2j.commons.config.ExProperties;
import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.math.MathUtil;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.enums.GeoType;
import net.sf.l2j.gameserver.geoengine.geodata.ABlock;
import net.sf.l2j.gameserver.geoengine.geodata.BlockComplex;
import net.sf.l2j.gameserver.geoengine.geodata.BlockComplexDynamic;
import net.sf.l2j.gameserver.geoengine.geodata.BlockFlat;
import net.sf.l2j.gameserver.geoengine.geodata.BlockMultilayer;
import net.sf.l2j.gameserver.geoengine.geodata.BlockMultilayerDynamic;
import net.sf.l2j.gameserver.geoengine.geodata.BlockNull;
import net.sf.l2j.gameserver.geoengine.geodata.GeoLocation;
import net.sf.l2j.gameserver.geoengine.geodata.GeoStructure;
import net.sf.l2j.gameserver.geoengine.geodata.IBlockDynamic;
import net.sf.l2j.gameserver.geoengine.geodata.IGeoObject;
import net.sf.l2j.gameserver.geoengine.pathfinding.Node;
import net.sf.l2j.gameserver.geoengine.pathfinding.NodeBuffer;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.instance.Door;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.network.serverpackets.ExServerPrimitive;

public class GeoEngine
{
	protected static final CLogger LOGGER = new CLogger(GeoEngine.class.getName());
	
	private static final String GEO_BUG = "%d;%d;%d;%d;%d;%d;%d;%s\r\n";
	
	private final ABlock[][] _blocks;
	private final BlockNull _nullBlock;
	
	private final PrintWriter _geoBugReports;
	
	// pre-allocated buffers
	private BufferHolder[] _buffers;
	
	// pathfinding statistics
	private int _findSuccess = 0;
	private int _findFails = 0;
	private int _postFilterPlayableUses = 0;
	private int _postFilterUses = 0;
	private long _postFilterElapsed = 0;
	
	/**
	 * GeoEngine contructor. Loads all geodata files of chosen geodata format.
	 */
	public GeoEngine()
	{
		// initialize block container
		_blocks = new ABlock[GeoStructure.GEO_BLOCKS_X][GeoStructure.GEO_BLOCKS_Y];
		
		// load null block
		_nullBlock = new BlockNull(GeoType.L2D);
		
		// initialize multilayer temporarily buffer
		BlockMultilayer.initialize();
		
		// load geo files according to geoengine config setup
		final ExProperties props = Config.initProperties(Config.GEOENGINE_FILE);
		int loaded = 0;
		int failed = 0;
		for (int rx = World.TILE_X_MIN; rx <= World.TILE_X_MAX; rx++)
		{
			for (int ry = World.TILE_Y_MIN; ry <= World.TILE_Y_MAX; ry++)
			{
				if (props.containsKey(String.valueOf(rx) + "_" + String.valueOf(ry)))
				{
					// region file is load-able, try to load it
					if (loadGeoBlocks(rx, ry))
						loaded++;
					else
						failed++;
				}
				else
				{
					// region file is not load-able, load null blocks
					loadNullBlocks(rx, ry);
				}
			}
		}
		LOGGER.info("Loaded {} L2D region files.", loaded);
		
		// release multilayer block temporarily buffer
		BlockMultilayer.release();
		
		if (failed > 0)
		{
			LOGGER.warn("Failed to load {} L2D region files. Please consider to check your \"geodata.properties\" settings and location of your geodata files.", failed);
			System.exit(1);
		}
		
		// initialize bug reports
		PrintWriter writer = null;
		try
		{
			writer = new PrintWriter(new FileOutputStream(new File(Config.GEODATA_PATH + "geo_bugs.txt"), true), true);
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't load \"geo_bugs.txt\" file.", e);
		}
		_geoBugReports = writer;
		
		String[] array = Config.PATHFIND_BUFFERS.split(";");
		_buffers = new BufferHolder[array.length];
		
		int count = 0;
		for (int i = 0; i < array.length; i++)
		{
			String buf = array[i];
			String[] args = buf.split("x");
			
			try
			{
				int size = Integer.parseInt(args[1]);
				count += size;
				_buffers[i] = new BufferHolder(Integer.parseInt(args[0]), size);
			}
			catch (Exception e)
			{
				LOGGER.error("Couldn't load buffer setting: {}.", e, buf);
			}
		}
		
		LOGGER.info("Loaded {} node buffers.", count);
	}
	
	/**
	 * Create list of node locations as result of calculated buffer node tree.
	 * @param target : the entry point
	 * @return LinkedList<NodeLoc> : list of node location
	 */
	private static final LinkedList<Location> constructPath(Node target)
	{
		// create empty list
		LinkedList<Location> list = new LinkedList<>();
		
		// set direction X/Y
		int dx = 0;
		int dy = 0;
		
		// get target parent
		Node parent = target.getParent();
		
		// while parent exists
		while (parent != null)
		{
			// get parent <> target direction X/Y
			final int nx = parent.getLoc().getGeoX() - target.getLoc().getGeoX();
			final int ny = parent.getLoc().getGeoY() - target.getLoc().getGeoY();
			
			// direction has changed?
			if (dx != nx || dy != ny)
			{
				// add node to the beginning of the list
				list.addFirst(target.getLoc());
				
				// update direction X/Y
				dx = nx;
				dy = ny;
			}
			
			// move to next node, set target and get its parent
			target = parent;
			parent = target.getParent();
		}
		
		// return list
		return list;
	}
	
	/**
	 * Provides optimize selection of the buffer. When all pre-initialized buffer are locked, creates new buffer and log this situation.
	 * @param size : pre-calculated minimal required size
	 * @param playable : moving object is playable?
	 * @return NodeBuffer : buffer
	 */
	private final NodeBuffer getBuffer(int size, boolean playable)
	{
		NodeBuffer current = null;
		for (BufferHolder holder : _buffers)
		{
			// Find proper size of buffer
			if (holder._size < size)
				continue;
			
			// Find unlocked NodeBuffer
			for (NodeBuffer buffer : holder._buffer)
			{
				if (!buffer.isLocked())
					continue;
				
				holder._uses++;
				if (playable)
					holder._playableUses++;
				
				holder._elapsed += buffer.getElapsedTime();
				return buffer;
			}
			
			// NodeBuffer not found, allocate temporary buffer
			current = new NodeBuffer(holder._size);
			current.isLocked();
			
			holder._overflows++;
			if (playable)
				holder._playableOverflows++;
		}
		
		return current;
	}
	
	/**
	 * Loads geodata from a file. When file does not exist, is corrupted or not consistent, loads none geodata.
	 * @param regionX : Geodata file region X coordinate.
	 * @param regionY : Geodata file region Y coordinate.
	 * @return boolean : True, when geodata file was loaded without problem.
	 */
	private final boolean loadGeoBlocks(int regionX, int regionY)
	{
		final String filename = String.format(GeoType.L2D.getFilename(), regionX, regionY);
		final String filepath = Config.GEODATA_PATH + filename;
		
		// standard load
		try (RandomAccessFile raf = new RandomAccessFile(filepath, "r");
			FileChannel fc = raf.getChannel())
		{
			// initialize file buffer
			MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size()).load();
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			
			// get block indexes
			final int blockX = (regionX - World.TILE_X_MIN) * GeoStructure.REGION_BLOCKS_X;
			final int blockY = (regionY - World.TILE_Y_MIN) * GeoStructure.REGION_BLOCKS_Y;
			
			// loop over region blocks
			for (int ix = 0; ix < GeoStructure.REGION_BLOCKS_X; ix++)
			{
				for (int iy = 0; iy < GeoStructure.REGION_BLOCKS_Y; iy++)
				{
					// get block type
					final byte type = buffer.get();
					
					// load block according to block type
					switch (type)
					{
						case GeoStructure.TYPE_FLAT_L2D:
							_blocks[blockX + ix][blockY + iy] = new BlockFlat(buffer, GeoType.L2D);
							break;
						
						case GeoStructure.TYPE_COMPLEX_L2D:
							_blocks[blockX + ix][blockY + iy] = new BlockComplex(buffer, GeoType.L2D);
							break;
						
						case GeoStructure.TYPE_MULTILAYER_L2D:
							_blocks[blockX + ix][blockY + iy] = new BlockMultilayer(buffer, GeoType.L2D);
							break;
						
						default:
							throw new IllegalArgumentException("Unknown block type: " + type);
					}
				}
			}
			
			// check data consistency
			if (buffer.remaining() > 0)
				LOGGER.warn("Region file {} can be corrupted, remaining {} bytes to read.", filename, buffer.remaining());
			
			// loading was successful
			return true;
		}
		catch (Exception e)
		{
			// an error occured while loading, load null blocks
			LOGGER.error("Error loading {} region file.", e, filename);
			
			// replace whole region file with null blocks
			loadNullBlocks(regionX, regionY);
			
			// loading was not successful
			return false;
		}
	}
	
	/**
	 * Loads null blocks. Used when no region file is detected or an error occurs during loading.
	 * @param regionX : Geodata file region X coordinate.
	 * @param regionY : Geodata file region Y coordinate.
	 */
	private final void loadNullBlocks(int regionX, int regionY)
	{
		// get block indexes
		final int blockX = (regionX - World.TILE_X_MIN) * GeoStructure.REGION_BLOCKS_X;
		final int blockY = (regionY - World.TILE_Y_MIN) * GeoStructure.REGION_BLOCKS_Y;
		
		// load all null blocks
		for (int ix = 0; ix < GeoStructure.REGION_BLOCKS_X; ix++)
			for (int iy = 0; iy < GeoStructure.REGION_BLOCKS_Y; iy++)
				_blocks[blockX + ix][blockY + iy] = _nullBlock;
	}
	
	/**
	 * Converts world X to geodata X.
	 * @param worldX
	 * @return int : Geo X
	 */
	public static final int getGeoX(int worldX)
	{
		return (MathUtil.limit(worldX, World.WORLD_X_MIN, World.WORLD_X_MAX) - World.WORLD_X_MIN) >> 4;
	}
	
	/**
	 * Converts world Y to geodata Y.
	 * @param worldY
	 * @return int : Geo Y
	 */
	public static final int getGeoY(int worldY)
	{
		return (MathUtil.limit(worldY, World.WORLD_Y_MIN, World.WORLD_Y_MAX) - World.WORLD_Y_MIN) >> 4;
	}
	
	/**
	 * Converts geodata X to world X.
	 * @param geoX
	 * @return int : World X
	 */
	public static final int getWorldX(int geoX)
	{
		return (MathUtil.limit(geoX, 0, GeoStructure.GEO_CELLS_X) << 4) + World.WORLD_X_MIN + 8;
	}
	
	/**
	 * Converts geodata Y to world Y.
	 * @param geoY
	 * @return int : World Y
	 */
	public static final int getWorldY(int geoY)
	{
		return (MathUtil.limit(geoY, 0, GeoStructure.GEO_CELLS_Y) << 4) + World.WORLD_Y_MIN + 8;
	}
	
	/**
	 * Returns block of geodata on given coordinates.
	 * @param geoX : Geodata X
	 * @param geoY : Geodata Y
	 * @return {@link ABlock} : Bloack of geodata.
	 */
	public final ABlock getBlock(int geoX, int geoY)
	{
		return _blocks[geoX / GeoStructure.BLOCK_CELLS_X][geoY / GeoStructure.BLOCK_CELLS_Y];
	}
	
	/**
	 * Check if geo coordinates has geo.
	 * @param geoX : Geodata X
	 * @param geoY : Geodata Y
	 * @return boolean : True, if given geo coordinates have geodata
	 */
	public final boolean hasGeoPos(int geoX, int geoY)
	{
		return getBlock(geoX, geoY).hasGeoPos();
	}
	
	/**
	 * Returns the height of cell, which is closest to given coordinates.
	 * @param geoX : Cell geodata X coordinate.
	 * @param geoY : Cell geodata Y coordinate.
	 * @param worldZ : Cell world Z coordinate.
	 * @return short : Cell geodata Z coordinate, closest to given coordinates.
	 */
	public final short getHeightNearest(int geoX, int geoY, int worldZ)
	{
		return getBlock(geoX, geoY).getHeightNearest(geoX, geoY, worldZ, null);
	}
	
	/**
	 * Returns the height of cell, which is closest to given coordinates.<br>
	 * Note: Ignores geodata modification of given {@link IGeoObject}.
	 * @param geoX : Cell geodata X coordinate.
	 * @param geoY : Cell geodata Y coordinate.
	 * @param worldZ : Cell world Z coordinate.
	 * @param ignore : The {@link IGeoObject}, which geodata modification is ignored and original geodata picked instead.
	 * @return short : Cell geodata Z coordinate, closest to given coordinates.
	 */
	public final short getHeightNearest(int geoX, int geoY, int worldZ, IGeoObject ignore)
	{
		return getBlock(geoX, geoY).getHeightNearest(geoX, geoY, worldZ, ignore);
	}
	
	/**
	 * Returns the NSWE flag byte of cell, which is closes to given coordinates.
	 * @param geoX : Cell geodata X coordinate.
	 * @param geoY : Cell geodata Y coordinate.
	 * @param worldZ : Cell world Z coordinate.
	 * @return short : Cell NSWE flag byte coordinate, closest to given coordinates.
	 */
	public final byte getNsweNearest(int geoX, int geoY, int worldZ)
	{
		return getBlock(geoX, geoY).getNsweNearest(geoX, geoY, worldZ, null);
	}
	
	/**
	 * Returns the NSWE flag byte of cell, which is closes to given coordinates.<br>
	 * Note: Ignores geodata modification of given {@link IGeoObject}.
	 * @param geoX : Cell geodata X coordinate.
	 * @param geoY : Cell geodata Y coordinate.
	 * @param worldZ : Cell world Z coordinate.
	 * @param ignore : The {@link IGeoObject}, which geodata modification is ignored and original geodata picked instead.
	 * @return short : Cell NSWE flag byte coordinate, closest to given coordinates.
	 */
	public final byte getNsweNearest(int geoX, int geoY, int worldZ, IGeoObject ignore)
	{
		return getBlock(geoX, geoY).getNsweNearest(geoX, geoY, worldZ, ignore);
	}
	
	/**
	 * Check if world coordinates has geo.
	 * @param worldX : World X
	 * @param worldY : World Y
	 * @return boolean : True, if given world coordinates have geodata
	 */
	public final boolean hasGeo(int worldX, int worldY)
	{
		return hasGeoPos(getGeoX(worldX), getGeoY(worldY));
	}
	
	/**
	 * Returns closest Z coordinate according to geodata.
	 * @param loc : The location used as reference.
	 * @return short : nearest Z coordinates according to geodata
	 */
	public final short getHeight(Location loc)
	{
		return getHeightNearest(getGeoX(loc.getX()), getGeoY(loc.getY()), loc.getZ());
	}
	
	/**
	 * Returns closest Z coordinate according to geodata.
	 * @param worldX : world x
	 * @param worldY : world y
	 * @param worldZ : world z
	 * @return short : nearest Z coordinates according to geodata
	 */
	public final short getHeight(int worldX, int worldY, int worldZ)
	{
		return getHeightNearest(getGeoX(worldX), getGeoY(worldY), worldZ);
	}
	
	/**
	 * Returns calculated NSWE flag byte as a description of {@link IGeoObject}.<br>
	 * The {@link IGeoObject} is defined by boolean 2D array, saying if the object is present on given cell or not.
	 * @param inside : 2D description of {@link IGeoObject}
	 * @return byte[][] : Returns NSWE flags of {@link IGeoObject}.
	 */
	public static final byte[][] calculateGeoObject(boolean inside[][])
	{
		// get dimensions
		final int width = inside.length;
		final int height = inside[0].length;
		
		// create object flags for geodata, according to the geo object 2D description
		final byte[][] result = new byte[width][height];
		
		// loop over each cell of the geo object
		for (int ix = 0; ix < width; ix++)
			for (int iy = 0; iy < height; iy++)
				if (inside[ix][iy])
				{
					// cell is inside geo object, block whole movement (nswe = 0)
					result[ix][iy] = 0;
				}
				else
				{
					// cell is outside of geo object, block only movement leading inside geo object
					
					// set initial value -> no geodata change
					byte nswe = (byte) 0xFF;
					
					// perform axial and diagonal checks
					if (iy < height - 1)
						if (inside[ix][iy + 1])
							nswe &= ~GeoStructure.CELL_FLAG_S;
					if (iy > 0)
						if (inside[ix][iy - 1])
							nswe &= ~GeoStructure.CELL_FLAG_N;
					if (ix < width - 1)
						if (inside[ix + 1][iy])
							nswe &= ~GeoStructure.CELL_FLAG_E;
					if (ix > 0)
						if (inside[ix - 1][iy])
							nswe &= ~GeoStructure.CELL_FLAG_W;
					if (ix < (width - 1) && iy < (height - 1))
						if (inside[ix + 1][iy + 1] || inside[ix][iy + 1] || inside[ix + 1][iy])
							nswe &= ~GeoStructure.CELL_FLAG_SE;
					if (ix < (width - 1) && iy > 0)
						if (inside[ix + 1][iy - 1] || inside[ix][iy - 1] || inside[ix + 1][iy])
							nswe &= ~GeoStructure.CELL_FLAG_NE;
					if (ix > 0 && iy < (height - 1))
						if (inside[ix - 1][iy + 1] || inside[ix][iy + 1] || inside[ix - 1][iy])
							nswe &= ~GeoStructure.CELL_FLAG_SW;
					if (ix > 0 && iy > 0)
						if (inside[ix - 1][iy - 1] || inside[ix][iy - 1] || inside[ix - 1][iy])
							nswe &= ~GeoStructure.CELL_FLAG_NW;
						
					result[ix][iy] = nswe;
				}
			
		return result;
	}
	
	/**
	 * Add {@link IGeoObject} to the geodata.
	 * @param object : An object using {@link IGeoObject} interface.
	 */
	public final void addGeoObject(IGeoObject object)
	{
		toggleGeoObject(object, true);
	}
	
	/**
	 * Remove {@link IGeoObject} from the geodata.
	 * @param object : An object using {@link IGeoObject} interface.
	 */
	public final void removeGeoObject(IGeoObject object)
	{
		toggleGeoObject(object, false);
	}
	
	/**
	 * Toggles an {@link IGeoObject} in the geodata.
	 * @param object : An object using {@link IGeoObject} interface.
	 * @param add : Add/remove object.
	 */
	private final void toggleGeoObject(IGeoObject object, boolean add)
	{
		// get object geo coordinates and data
		final int minGX = object.getGeoX();
		final int minGY = object.getGeoY();
		final byte[][] geoData = object.getObjectGeoData();
		
		// get min/max block coordinates
		int minBX = minGX / GeoStructure.BLOCK_CELLS_X;
		int maxBX = (minGX + geoData.length - 1) / GeoStructure.BLOCK_CELLS_X;
		int minBY = minGY / GeoStructure.BLOCK_CELLS_Y;
		int maxBY = (minGY + geoData[0].length - 1) / GeoStructure.BLOCK_CELLS_Y;
		
		// loop over affected blocks in X direction
		for (int bx = minBX; bx <= maxBX; bx++)
		{
			// loop over affected blocks in Y direction
			for (int by = minBY; by <= maxBY; by++)
			{
				ABlock block;
				
				// conversion to dynamic block must be synchronized to prevent 2 independent threads converting same block
				synchronized (_blocks)
				{
					// get related block
					block = _blocks[bx][by];
					
					// check for dynamic block
					if (!(block instanceof IBlockDynamic))
					{
						// null block means no geodata (particular region file is not loaded), no geodata means no geobjects
						if (block instanceof BlockNull)
							continue;
						
						// not a dynamic block, convert it
						if (block instanceof BlockFlat)
						{
							// convert flat block to the dynamic complex block
							block = new BlockComplexDynamic(bx, by, (BlockFlat) block);
							_blocks[bx][by] = block;
						}
						else if (block instanceof BlockComplex)
						{
							// convert complex block to the dynamic complex block
							block = new BlockComplexDynamic(bx, by, (BlockComplex) block);
							_blocks[bx][by] = block;
						}
						else if (block instanceof BlockMultilayer)
						{
							// convert multilayer block to the dynamic multilayer block
							block = new BlockMultilayerDynamic(bx, by, (BlockMultilayer) block);
							_blocks[bx][by] = block;
						}
					}
				}
				
				// add/remove geo object to/from dynamic block
				if (add)
					((IBlockDynamic) block).addGeoObject(object);
				else
					((IBlockDynamic) block).removeGeoObject(object);
			}
		}
	}
	
	/**
	 * Check line of sight from {@link WorldObject} to {@link WorldObject}.<br>
	 * Note: If target is {@link IGeoObject} (e.g. {@link Door}), it ignores its geodata modification.
	 * @param object : The origin object.
	 * @param target : The target object.
	 * @return True, when object can see target.
	 */
	public final boolean canSeeTarget(WorldObject object, WorldObject target)
	{
		return canSeeTarget(object, target, null);
	}
	
	/**
	 * Check line of sight from {@link WorldObject} to {@link WorldObject}.<br>
	 * Note: If target is {@link IGeoObject} (e.g. {@link Door}), it ignores its geodata modification.
	 * @param object : The origin object.
	 * @param target : The target object.
	 * @param debug : The debug packet to add debug informations in.
	 * @return True, when object can see target.
	 */
	public final boolean canSeeTarget(WorldObject object, WorldObject target, ExServerPrimitive debug)
	{
		// Get object's and position's world coordinates.
		final int ox = object.getX();
		final int oy = object.getY();
		final int oz = object.getZ();
		final int tx = target.getX();
		final int ty = target.getY();
		final int tz = target.getZ();
		
		// Get object's geo coordinates and check them.
		final int gox = getGeoX(ox);
		final int goy = getGeoY(oy);
		if (!hasGeoPos(gox, goy))
			return true;
		
		final short goz = getHeightNearest(gox, goy, oz);
		
		// Get target's geo coordinates and check them.
		final int gtx = getGeoX(tx);
		final int gty = getGeoY(ty);
		if (!hasGeoPos(gtx, gty))
			return true;
		
		final IGeoObject ignore = target instanceof IGeoObject ? (IGeoObject) target : null;
		final short gtz = getHeightNearest(gtx, gty, tz, ignore);
		
		// Check for object and target are on same cell.
		if (gox == gtx && goy == gty)
			return goz == gtz;
			
		// Get object's and target's line of sight height (if relevant).
		// Note: Real creature height = collision height * 2.
		double losoz = goz;
		if (object instanceof Creature)
			losoz += ((Creature) object).getCollisionHeight() * 2 * Config.PART_OF_CHARACTER_HEIGHT / 100;
		
		double lostz = gtz;
		if (target instanceof Creature)
			lostz += ((Creature) target).getCollisionHeight() * 2 * Config.PART_OF_CHARACTER_HEIGHT / 100;
		
		// Perform geodata check.
		return canSee(gox, goy, goz, losoz, gtx, gty, gtz, lostz, ignore, debug);
	}
	
	/**
	 * Check line of sight from {@link WorldObject} to {@link Location}.<br>
	 * Note: The check uses {@link Location}'s real Z coordinate (e.g. point above ground), not its geodata representation.
	 * @param object : The origin object.
	 * @param position : The target position.
	 * @return True, when object can see position.
	 */
	public final boolean canSeeLocation(WorldObject object, Location position)
	{
		// Get object's and position's world coordinates.
		final int ox = object.getX();
		final int oy = object.getY();
		final int oz = object.getZ();
		final int tx = position.getX();
		final int ty = position.getY();
		final int tz = position.getZ();
		
		// Get object's geo coordinates and check them.
		final int gox = getGeoX(ox);
		final int goy = getGeoY(oy);
		if (!hasGeoPos(gox, goy))
			return true;
		
		final short goz = getHeightNearest(gox, goy, oz);
		
		// Get position's geo coordinates and check them.
		final int gtx = getGeoX(tx);
		final int gty = getGeoY(ty);
		if (!hasGeoPos(gtx, gty))
			return true;
		
		final short gtz = getHeightNearest(gtx, gty, tz);
		
		// Check for object and position are on same cell.
		if (gox == gtx && goy == gty)
			return goz == gtz;
			
		// Get object's line of sight height (if relevant).
		// Note: Real creature height = collision height * 2.
		double losoz = goz;
		if (object instanceof Creature)
			losoz += ((Creature) object).getCollisionHeight() * 2 * Config.PART_OF_CHARACTER_HEIGHT / 100;
		
		// Perform geodata check.
		return canSee(gox, goy, goz, losoz, gtx, gty, gtz, tz, null, null);
	}
	
	/**
	 * Simple check for origin to target visibility.<br>
	 * Note: Ignores geodata modification of given {@link IGeoObject}.
	 * @param gox : Origin X geodata coordinate.
	 * @param goy : Origin Y geodata coordinate.
	 * @param goz : Origin Z geodata coordinate.
	 * @param losoz : Origin line of sight height (absolute coordinates).
	 * @param gtx : Target X geodata coordinate.
	 * @param gty : Target Y geodata coordinate.
	 * @param gtz : Target Z geodata coordinate.
	 * @param lostz : Target line of sight height (absolute coordinates).
	 * @param debug : The debug packet to add debug informations in.
	 * @param ignore : The {@link IGeoObject}, which geodata modification is ignored and original geodata picked instead.
	 * @return True, when origin can see target.
	 */
	protected final boolean canSee(int gox, int goy, int goz, double losoz, int gtx, int gty, int gtz, double lostz, IGeoObject ignore, ExServerPrimitive debug)
	{
		// Get X delta, signum and direction flags.
		final int dx = Math.abs(gtx - gox);
		final int sx = gox < gtx ? 1 : -1;
		final byte dirox = sx > 0 ? GeoStructure.CELL_FLAG_E : GeoStructure.CELL_FLAG_W;
		final byte dirtx = sx > 0 ? GeoStructure.CELL_FLAG_W : GeoStructure.CELL_FLAG_E;
		
		// Get Y delta, signum and direction flags.
		final int dy = Math.abs(gty - goy);
		final int sy = goy < gty ? 1 : -1;
		final byte diroy = sy > 0 ? GeoStructure.CELL_FLAG_S : GeoStructure.CELL_FLAG_N;
		final byte dirty = sy > 0 ? GeoStructure.CELL_FLAG_N : GeoStructure.CELL_FLAG_S;
		
		// Get Z delta.
		final int dm = Math.max(dx, dy);
		final double dz = (lostz - losoz) / dm;
		
		// Get direction flag for diagonal movement.
		final byte diroxy = getDirXY(dirox, diroy);
		final byte dirtxy = getDirXY(dirtx, dirty);
		
		// Get delta, determines axis to move on (+..X axis, -..Y axis).
		int d = dx - dy;
		
		// Get direction flags of iteration and NSWE of first cells.
		byte diro = 0;
		byte dirt = 0;
		byte nsweo = getNsweNearest(gox, goy, goz, ignore);
		byte nswet = getNsweNearest(gtx, gty, gtz, ignore);
		
		// Run loop.
		ABlock block;
		int index;
		for (int i = 0; i < (dm + 1) / 2; i++)
		{
			// Add points to debug packet, if present.
			if (debug != null)
			{
				debug.addPoint(Color.CYAN, getWorldX(gox), getWorldY(goy), goz - 24);
				debug.addPoint(Color.YELLOW, getWorldX(gtx), getWorldY(gty), gtz - 24);
			}
			
			// Calculate next point coordinates.
			int e2 = 2 * d;
			if (e2 > -dy && e2 < dx)
			{
				// Next point has different XY coordinates.
				d -= dy;
				d += dx;
				gox += sx;
				gtx -= sx;
				goy += sy;
				gty -= sy;
				diro = diroxy;
				dirt = dirtxy;
			}
			else if (e2 > -dy)
			{
				// Next point has different X coordinate.
				d -= dy;
				gox += sx;
				gtx -= sx;
				diro = dirox;
				dirt = dirtx;
			}
			else if (e2 < dx)
			{
				// Next point has different Y coordinate.
				d += dx;
				goy += sy;
				gty -= sy;
				diro = diroy;
				dirt = dirty;
			}
			
			{
				// Get block of the next cell.
				block = getBlock(gox, goy);
				
				// Get index of particular layer, based on movement conditions.
				if ((nsweo & diro) == 0)
					index = block.getIndexAbove(gox, goy, goz - GeoStructure.CELL_IGNORE_HEIGHT, ignore);
				else
					index = block.getIndexBelow(gox, goy, goz + GeoStructure.CELL_IGNORE_HEIGHT, ignore);
				
				// Next cell's layer does not exist (no geodata with valid condition), return.
				if (index < 0)
					return false;
				
				// Get next cell's line of sight Z coordinate.
				goz = block.getHeight(index, ignore);
				losoz += dz;
				
				// Perform line of sight check, return when fails.
				if ((goz - losoz) > Config.MAX_OBSTACLE_HEIGHT)
					return false;
				
				// Get next cell's NSWE.
				nsweo = block.getNswe(index, ignore);
			}
			{
				// Get block of the next cell.
				block = getBlock(gtx, gty);
				
				// Get index of particular layer, based on movement conditions.
				if ((nswet & dirt) == 0)
					index = block.getIndexAbove(gtx, gty, gtz - GeoStructure.CELL_IGNORE_HEIGHT, ignore);
				else
					index = block.getIndexBelow(gtx, gty, gtz + GeoStructure.CELL_IGNORE_HEIGHT, ignore);
				
				// Next cell's layer does not exist (no geodata with valid condition), return.
				if (index < 0)
					return false;
				
				// Get next cell's line of sight Z coordinate.
				gtz = block.getHeight(index, ignore);
				lostz -= dz;
				
				// Perform line of sight check, return when fails.
				if ((gtz - lostz) > Config.MAX_OBSTACLE_HEIGHT)
					return false;
				
				// Get next cell's NSWE.
				nswet = block.getNswe(index, ignore);
			}
		}
		
		// Iteration is completed, no obstacle is found.
		return true;
	}
	
	/**
	 * Check movement of {@link WorldObject} to {@link WorldObject}.
	 * @param object : The origin object.
	 * @param target : The target object.
	 * @return True, when the path is clear.
	 */
	public final boolean canMoveToTarget(WorldObject object, WorldObject target)
	{
		return canMoveToTarget(object.getPosition(), target.getPosition());
	}
	
	/**
	 * Check movement of {@link WorldObject} to {@link Location}.
	 * @param object : The origin object.
	 * @param position : The target position.
	 * @return True, when the path is clear.
	 */
	public final boolean canMoveToTarget(WorldObject object, Location position)
	{
		return canMoveToTarget(object.getPosition(), position);
	}
	
	/**
	 * Check movement of {@link Location} to {@link Location}.
	 * @param origin : The origin position.
	 * @param target : The target position.
	 * @return True, when the path is clear.
	 */
	public final boolean canMoveToTarget(Location origin, Location target)
	{
		return canMoveToTarget(origin.getX(), origin.getY(), origin.getZ(), target.getX(), target.getY(), target.getZ());
	}
	
	/**
	 * Check movement from coordinates to coordinates.
	 * @param ox : Origin X coordinate.
	 * @param oy : Origin Y coordinate.
	 * @param oz : Origin Z coordinate.
	 * @param tx : Target X coordinate.
	 * @param ty : Target Y coordinate.
	 * @param tz : Target Z coordinate.
	 * @return True, when target coordinates are reachable from origin coordinates.
	 */
	public final boolean canMoveToTarget(int ox, int oy, int oz, int tx, int ty, int tz)
	{
		// Get origin's geo coordinates and check them.
		final int gox = getGeoX(ox);
		final int goy = getGeoY(oy);
		if (!hasGeoPos(gox, goy))
			return true;
		
		final short goz = getHeightNearest(gox, goy, oz);
		
		// Get target's geo coordinates and check them.
		final int gtx = getGeoX(tx);
		final int gty = getGeoY(ty);
		if (!hasGeoPos(gtx, gty))
			return true;
		
		final short gtz = getHeightNearest(gtx, gty, tz);
		
		// Check for origin and target are on same cell.
		if (gox == gtx && goy == gty && goz == gtz)
			return true;
		
		// Perform geodata check.
		return canMove(gox, goy, goz, gtx, gty, gtz);
	}
	
	/**
	 * Simple check for origin to target movement.
	 * @param gox : Origin X geodata coordinate.
	 * @param goy : Origin Y geodata coordinate.
	 * @param goz : Origin Z geodata coordinate.
	 * @param gtx : Target X geodata coordinate.
	 * @param gty : Target Y geodata coordinate.
	 * @param gtz : Target Z geodata coordinate.
	 * @return True, when target coordinates are reachable from origin coordinates.
	 */
	protected final boolean canMove(int gox, int goy, short goz, int gtx, int gty, short gtz)
	{
		// Get X delta, signum and direction flags.
		final int dx = Math.abs(gtx - gox);
		final int sx = gox < gtx ? 1 : -1;
		final byte dirX = sx > 0 ? GeoStructure.CELL_FLAG_E : GeoStructure.CELL_FLAG_W;
		
		// Get Y delta, signum and direction flags.
		final int dy = Math.abs(gty - goy);
		final int sy = goy < gty ? 1 : -1;
		final byte dirY = sy > 0 ? GeoStructure.CELL_FLAG_S : GeoStructure.CELL_FLAG_N;
		
		// Get direction flag for diagonal movement.
		final byte dirXY = getDirXY(dirX, dirY);
		
		// Get delta, determines axis to move on (+..X axis, -..Y axis).
		int d = dx - dy;
		
		// NSWE direction of movement (iteration).
		byte direction = 0;
		
		// Load next pointer.
		int nx = gox;
		int ny = goy;
		
		// Run until target coordinates reached.
		while (gox != gtx || goy != gty)
		{
			// Calculate next point coordinates.
			int e2 = 2 * d;
			if (e2 > -dy && e2 < dx)
			{
				// Next point has different XY coordinates.
				d -= dy;
				d += dx;
				nx += sx;
				ny += sy;
				direction = dirXY;
			}
			else if (e2 > -dy)
			{
				// Next point has different X coordinate.
				d -= dy;
				nx += sx;
				direction = dirX;
			}
			else if (e2 < dx)
			{
				// Next point has different Y coordinate.
				d += dx;
				ny += sy;
				direction = dirY;
			}
			
			// Check point heading into obstacle, if so return current point.
			if ((getNsweNearest(gox, goy, goz) & direction) == 0)
				return false;
			
			// Check next point for extensive Z difference, if so return current point.
			final ABlock block = getBlock(nx, ny);
			final int i = block.getIndexBelow(nx, ny, goz + GeoStructure.CELL_IGNORE_HEIGHT, null);
			if (i < 0)
				return false;
			
			// Update next point's coordinates.
			gox = nx;
			goy = ny;
			goz = block.getHeight(i, null);
		}
		
		// When Z coordinates are same, path is okay. Otherwise not.
		return goz == gtz;
	}
	
	/**
	 * Check movement of object to target coordinates. Returns last accessible point in the checked path.<br>
	 * Target X and Y reachable and Z is on same floor:
	 * <ul>
	 * <li>Location of the target with corrected Z value from geodata.</li>
	 * </ul>
	 * Target X and Y reachable but Z is on another floor:
	 * <ul>
	 * <li>Location of the origin with corrected Z value from geodata.</li>
	 * </ul>
	 * Target X and Y not reachable:
	 * <ul>
	 * <li>Last accessible location in destination to target.</li>
	 * </ul>
	 * @param object : Origin object.
	 * @param tx : Target X coordinate.
	 * @param ty : Target Y coordinate.
	 * @param tz : Target Z coordinate.
	 * @return The {@link Location} representing last point of movement (e.g. just before wall).
	 */
	public final Location getValidLocation(WorldObject object, int tx, int ty, int tz)
	{
		return getValidLocation(object.getX(), object.getY(), object.getZ(), tx, ty, tz, null);
	}
	
	/**
	 * Check movement of object to target. Returns last accessible point in the checked path.<br>
	 * Target X and Y reachable and Z is on same floor:
	 * <ul>
	 * <li>Location of the target with corrected Z value from geodata.</li>
	 * </ul>
	 * Target X and Y reachable but Z is on another floor:
	 * <ul>
	 * <li>Location of the origin with corrected Z value from geodata.</li>
	 * </ul>
	 * Target X and Y not reachable:
	 * <ul>
	 * <li>Last accessible location in destination to target.</li>
	 * </ul>
	 * @param follower : Origin object.
	 * @param pawn : Target object.
	 * @return The {@link Location} representing last point of movement (e.g. just before wall).
	 */
	public final Location getValidLocation(WorldObject follower, WorldObject pawn)
	{
		return getValidLocation(follower.getPosition(), pawn.getPosition());
	}
	
	/**
	 * Check movement of object to target position. Returns last accessible point in the checked path.<br>
	 * Target X and Y reachable and Z is on same floor:
	 * <ul>
	 * <li>Location of the target with corrected Z value from geodata.</li>
	 * </ul>
	 * Target X and Y reachable but Z is on another floor:
	 * <ul>
	 * <li>Location of the origin with corrected Z value from geodata.</li>
	 * </ul>
	 * Target X and Y not reachable:
	 * <ul>
	 * <li>Last accessible location in destination to target.</li>
	 * </ul>
	 * @param object : Origin object.
	 * @param position : Target position.
	 * @return The {@link Location} representing last point of movement (e.g. just before wall).
	 */
	public final Location getValidLocation(WorldObject object, Location position)
	{
		return getValidLocation(object.getPosition(), position);
	}
	
	/**
	 * Check movement from origin to target positions. Returns last accessible point in the checked path.<br>
	 * Target X and Y reachable and Z is on same floor:
	 * <ul>
	 * <li>Location of the target with corrected Z value from geodata.</li>
	 * </ul>
	 * Target X and Y reachable but Z is on another floor:
	 * <ul>
	 * <li>Location of the origin with corrected Z value from geodata.</li>
	 * </ul>
	 * Target X and Y not reachable:
	 * <ul>
	 * <li>Last accessible location in destination to target.</li>
	 * </ul>
	 * @param origin : Origin position.
	 * @param target : Target position.
	 * @return The {@link Location} representing last point of movement (e.g. just before wall).
	 */
	public final Location getValidLocation(Location origin, Location target)
	{
		return getValidLocation(origin.getX(), origin.getY(), origin.getZ(), target.getX(), target.getY(), target.getZ(), null);
	}
	
	/**
	 * Check movement from origin to target coordinates. Returns last available point in the checked path.<br>
	 * Target X and Y reachable and Z is on same floor:
	 * <ul>
	 * <li>Location of the target with corrected Z value from geodata.</li>
	 * </ul>
	 * Target X and Y reachable but Z is on another floor:
	 * <ul>
	 * <li>Location of the origin with corrected Z value from geodata.</li>
	 * </ul>
	 * Target X and Y not reachable:
	 * <ul>
	 * <li>Last accessible location in destination to target.</li>
	 * </ul>
	 * @param ox : Origin X coordinate.
	 * @param oy : Origin Y coordinate.
	 * @param oz : Origin Z coordinate.
	 * @param tx : Target X coordinate.
	 * @param ty : Target Y coordinate.
	 * @param tz : Target Z coordinate.
	 * @param debug : The debug packet to add debug informations in.
	 * @return The {@link Location} representing last point of movement (e.g. just before wall).
	 */
	public final Location getValidLocation(int ox, int oy, int oz, int tx, int ty, int tz, ExServerPrimitive debug)
	{
		// Get origin's geo coordinates and check them.
		int gox = getGeoX(ox);
		int goy = getGeoY(oy);
		if (!hasGeoPos(gox, goy))
			return new Location(tx, ty, tz);
		
		short goz = getHeightNearest(gox, goy, oz);
		
		// Get target's geo coordinates and check them.
		final int gtx = getGeoX(tx);
		final int gty = getGeoY(ty);
		if (!hasGeoPos(gtx, gty))
			return new Location(tx, ty, tz);
		
		final short gtz = getHeightNearest(gtx, gty, tz);
		
		// Check for origin and target are on same cell.
		if (gox == gtx && goy == gty && goz == gtz)
			return new Location(tx, ty, tz);
		
		// Get X delta, signum and direction flags.
		final int dx = Math.abs(gtx - gox);
		final int sx = gox < gtx ? 1 : -1;
		final byte dirX = sx > 0 ? GeoStructure.CELL_FLAG_E : GeoStructure.CELL_FLAG_W;
		
		// Get Y delta, signum and direction flags.
		final int dy = Math.abs(gty - goy);
		final int sy = goy < gty ? 1 : -1;
		final byte dirY = sy > 0 ? GeoStructure.CELL_FLAG_S : GeoStructure.CELL_FLAG_N;
		
		// Get direction flag for diagonal movement.
		final byte dirXY = getDirXY(dirX, dirY);
		
		// Get delta, determines axis to move on (+..X axis, -..Y axis).
		int d = dx - dy;
		
		// NSWE direction of movement (iteration).
		byte direction = 0;
		
		// Load next pointer.
		int nx = gox;
		int ny = goy;
		
		// Run until target coordinates reached.
		while (gox != gtx || goy != gty)
		{
			// Add points to debug packet, if present.
			if (debug != null)
				debug.addPoint(Color.GREEN, getWorldX(gox), getWorldY(goy), goz - 24);
			
			// Calculate next point coordinates.
			int e2 = 2 * d;
			if (e2 > -dy && e2 < dx)
			{
				// Next point has different XY coordinates.
				d -= dy;
				d += dx;
				nx += sx;
				ny += sy;
				direction = dirXY;
			}
			else if (e2 > -dy)
			{
				// Next point has different X coordinate.
				d -= dy;
				nx += sx;
				direction = dirX;
			}
			else if (e2 < dx)
			{
				// Next point has different Y coordinate.
				d += dx;
				ny += sy;
				direction = dirY;
			}
			
			// Check point heading into obstacle, if so return current point.
			if ((getNsweNearest(gox, goy, goz) & direction) == 0)
				return calculateBorderPoint(ox, oy, tx, ty, sx, sy, gox, goy, goz);
			
			// Check next point for extensive Z difference, if so return current point.
			final ABlock block = getBlock(nx, ny);
			final int i = block.getIndexBelow(nx, ny, goz + GeoStructure.CELL_IGNORE_HEIGHT, null);
			if (i < 0)
				return calculateBorderPoint(ox, oy, tx, ty, sx, sy, gox, goy, goz);
			
			// Update next point's coordinates.
			gox = nx;
			goy = ny;
			goz = block.getHeight(i, null);
		}
		
		// Compare Z coordinates:
		// If same, path is okay, return target point.
		// If not same, path is does not exist, return origin point.
		return goz == gtz ? new Location(tx, ty, tz) : new Location(ox, oy, oz);
	}
	
	/**
	 * @param dirX : The W-E flag (X axis).
	 * @param dirY : The N-S flag (Y axis).
	 * @return The diagonal NSWE flag of two combined axis flags (N-S and W-E).
	 */
	private static final byte getDirXY(byte dirX, byte dirY)
	{
		if (dirY == GeoStructure.CELL_FLAG_N)
		{
			if (dirX == GeoStructure.CELL_FLAG_W)
				return GeoStructure.CELL_FLAG_NW;
			
			return GeoStructure.CELL_FLAG_NE;
		}
		
		if (dirX == GeoStructure.CELL_FLAG_W)
			return GeoStructure.CELL_FLAG_SW;
		
		return GeoStructure.CELL_FLAG_SE;
	}
	
	/**
	 * @param ox : Origin X coordinate.
	 * @param oy : Origin Y coordinate.
	 * @param tx : Target X coordinate.
	 * @param ty : Target Y coordinate.
	 * @param sx : Signum of movement (iteration) in X axis.
	 * @param sy : Signum of movement (iteration) in Y axis.
	 * @param gpx : Iterated point X geodata coordinate.
	 * @param gpy : Iterated point Y geodata coordinate.
	 * @param gpz : Iterated point Z geodata coordinate.
	 * @return The {@link Location} inside iterated point, which has correct offset given to movement (iteration) direction.
	 */
	private static final Location calculateBorderPoint(int ox, int oy, int tx, int ty, int sx, int sy, int gpx, int gpy, short gpz)
	{
		// Get world Y coordinate of the point and update it to cell border (adjust by -8, +7 by given direction).
		int py = getWorldY(gpy);
		py += sy > 0 ? 7 : -8;
		
		// Calculate X coordinate and adjust it (rounding "up" based on direction, to not lose precision).
		double pxc = ox + (double) (tx - ox) * (py - oy) / (ty - oy);
		pxc = sx > 0 ? Math.ceil(pxc) : Math.floor(pxc);
		
		// Check XY coordinates. If calculated X is outside of point, calculate the other pair of XY.
		if (getGeoX((int) pxc) == gpx)
			// Return Location which lays on the current point in the direction of.
			return new Location((int) pxc, py, gpz);
		
		// Get world X coordinate of the point and update it to cell border (adjust by -8, +7 by given direction).
		int px = getWorldX(gpx);
		px += sx > 0 ? 7 : -8;
		
		// Calculate Y coordinate and adjust it (rounding "up" based on direction, to not lose precision).
		double pyc = oy + (double) (ty - oy) * (px - ox) / (tx - ox);
		pyc = sy > 0 ? Math.ceil(pyc) : Math.floor(pyc);
		
		// Check XY coordinates. If calculated X is outside of point, calculate the other pair of XY.
		if (getGeoY((int) pyc) == gpy)
			// Return Location which lays on the current point in the direction of.
			return new Location(px, (int) pyc, gpz);
		
		// No coordinates found (is possible, but rare occasions), set coordinates to the point.
		return new Location(px, py, gpz);
	}
	
	/**
	 * Returns the list of location objects as a result of complete path calculation.
	 * @param ox : origin x
	 * @param oy : origin y
	 * @param oz : origin z
	 * @param tx : target x
	 * @param ty : target y
	 * @param tz : target z
	 * @param playable : moving object is playable?
	 * @param debug : The debug packet to add debug informations in.
	 * @return {@code LinkedList<Location>} : complete path from nodes
	 */
	public LinkedList<Location> findPath(int ox, int oy, int oz, int tx, int ty, int tz, boolean playable, ExServerPrimitive debug)
	{
		// get origin and check existing geo coords
		int gox = getGeoX(ox);
		int goy = getGeoY(oy);
		if (!hasGeoPos(gox, goy))
			return null;
		
		short goz = getHeightNearest(gox, goy, oz);
		
		// get target and check existing geo coords
		int gtx = getGeoX(tx);
		int gty = getGeoY(ty);
		if (!hasGeoPos(gtx, gty))
			return null;
		
		short gtz = getHeightNearest(gtx, gty, tz);
		
		// Prepare buffer for pathfinding calculations
		NodeBuffer buffer = getBuffer(64 + (2 * Math.max(Math.abs(gox - gtx), Math.abs(goy - gty))), playable);
		if (buffer == null)
			return null;
		
		// find path
		LinkedList<Location> path = null;
		try
		{
			Node result = buffer.findPath(gox, goy, goz, gtx, gty, gtz);
			
			if (result == null)
			{
				_findFails++;
				return null;
			}
			
			if (debug != null)
			{
				// path origin and target
				debug.addPoint(Color.BLUE, ox, oy, oz);
				debug.addPoint(Color.BLUE, tx, ty, tz);
				
				// path
				for (Node n : buffer.debugPath())
				{
					int cost = (int) -n.getCost();
					if (cost > 0)
						debug.addPoint(String.valueOf(cost), Color.YELLOW, true, n.getLoc().getX(), n.getLoc().getY(), n.getLoc().getZ() - 16);
					else
						debug.addPoint(Color.BLUE, n.getLoc().getX(), n.getLoc().getY(), n.getLoc().getZ() - 16);
				}
			}
			
			path = constructPath(result);
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to generate a path.", e);
			
			_findFails++;
			return null;
		}
		finally
		{
			buffer.free();
			_findSuccess++;
		}
		
		// check path
		if (path.size() < 3)
			return path;
		
		// log data
		long timeStamp = System.currentTimeMillis();
		_postFilterUses++;
		if (playable)
			_postFilterPlayableUses++;
		
		// get path list iterator
		ListIterator<Location> point = path.listIterator();
		
		// get node A (origin)
		int nodeAx = gox;
		int nodeAy = goy;
		short nodeAz = goz;
		
		// get node B
		GeoLocation nodeB = (GeoLocation) point.next();
		
		// iterate thought the path to optimize it
		while (point.hasNext())
		{
			// get node C
			GeoLocation nodeC = (GeoLocation) path.get(point.nextIndex());
			
			// check movement from node A to node C
			if (canMove(nodeAx, nodeAy, nodeAz, nodeC.getGeoX(), nodeC.getGeoY(), (short) nodeC.getZ()))
			{
				// can move from node A to node C
				
				// remove node B
				point.remove();
				
				// show skipped nodes
				if (debug != null)
					debug.addPoint(Color.RED, nodeB.getX(), nodeB.getY(), nodeB.getZ());
			}
			else
			{
				// can not move from node A to node C
				
				// set node A (node B is part of path, update A coordinates)
				nodeAx = nodeB.getGeoX();
				nodeAy = nodeB.getGeoY();
				nodeAz = (short) nodeB.getZ();
				
				// show used nodes
				if (debug != null)
					debug.addPoint(Color.GREEN, nodeB.getX(), nodeB.getY(), nodeB.getZ());
			}
			
			// set node B
			nodeB = (GeoLocation) point.next();
		}
		
		// show final path
		if (debug != null)
		{
			Location prev = new Location(ox, oy, oz);
			int i = 1;
			for (Location next : path)
			{
				debug.addLine("Segment #" + i, Color.GREEN, true, prev, next);
				prev = next;
				i++;
			}
		}
		
		// log data
		_postFilterElapsed += System.currentTimeMillis() - timeStamp;
		
		return path;
	}
	
	/**
	 * Return pathfinding statistics, useful for getting information about pathfinding status.
	 * @return {@code List<String>} : stats
	 */
	public List<String> getStat()
	{
		List<String> list = new ArrayList<>();
		
		for (BufferHolder buffer : _buffers)
			list.add(buffer.toString());
		
		list.add("Use: playable=" + String.valueOf(_postFilterPlayableUses) + " non-playable=" + String.valueOf(_postFilterUses - _postFilterPlayableUses));
		
		if (_postFilterUses > 0)
			list.add("Time (ms): total=" + String.valueOf(_postFilterElapsed) + " avg=" + String.format("%1.2f", (double) _postFilterElapsed / _postFilterUses));
		
		list.add("Pathfind: success=" + String.valueOf(_findSuccess) + ", fail=" + String.valueOf(_findFails));
		
		return list;
	}
	
	/**
	 * Record a geodata bug.
	 * @param loc : Location of the geodata bug.
	 * @param comment : Short commentary.
	 * @return boolean : True, when bug was successfully recorded.
	 */
	public final boolean addGeoBug(Location loc, String comment)
	{
		int gox = getGeoX(loc.getX());
		int goy = getGeoY(loc.getY());
		int goz = loc.getZ();
		int rx = gox / GeoStructure.REGION_CELLS_X + World.TILE_X_MIN;
		int ry = goy / GeoStructure.REGION_CELLS_Y + World.TILE_Y_MIN;
		int bx = (gox / GeoStructure.BLOCK_CELLS_X) % GeoStructure.REGION_BLOCKS_X;
		int by = (goy / GeoStructure.BLOCK_CELLS_Y) % GeoStructure.REGION_BLOCKS_Y;
		int cx = gox % GeoStructure.BLOCK_CELLS_X;
		int cy = goy % GeoStructure.BLOCK_CELLS_Y;
		
		try
		{
			_geoBugReports.printf(GEO_BUG, rx, ry, bx, by, cx, cy, goz, comment.replace(";", ":"));
			return true;
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't save new entry to \"geo_bugs.txt\" file.", e);
			return false;
		}
	}
	
	/**
	 * NodeBuffer container with specified size and count of separate buffers.
	 */
	private static final class BufferHolder
	{
		final int _size;
		final int _count;
		ArrayList<NodeBuffer> _buffer;
		
		// statistics
		int _playableUses = 0;
		int _uses = 0;
		int _playableOverflows = 0;
		int _overflows = 0;
		long _elapsed = 0;
		
		public BufferHolder(int size, int count)
		{
			_size = size;
			_count = count;
			_buffer = new ArrayList<>(count);
			
			for (int i = 0; i < count; i++)
				_buffer.add(new NodeBuffer(size));
		}
		
		@Override
		public String toString()
		{
			final StringBuilder sb = new StringBuilder(100);
			
			StringUtil.append(sb, "Buffer ", String.valueOf(_size), "x", String.valueOf(_size), ": count=", String.valueOf(_count), " uses=", String.valueOf(_playableUses), "/", String.valueOf(_uses));
			
			if (_uses > 0)
				StringUtil.append(sb, " total/avg(ms)=", String.valueOf(_elapsed), "/", String.format("%1.2f", (double) _elapsed / _uses));
			
			StringUtil.append(sb, " ovf=", String.valueOf(_playableOverflows), "/", String.valueOf(_overflows));
			
			return sb.toString();
		}
	}
	
	/**
	 * Returns the instance of the {@link GeoEngine}.
	 * @return {@link GeoEngine} : The instance.
	 */
	public static final GeoEngine getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final GeoEngine INSTANCE = new GeoEngine();
	}
}