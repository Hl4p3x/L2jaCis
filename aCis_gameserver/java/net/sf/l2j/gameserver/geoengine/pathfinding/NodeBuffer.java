package net.sf.l2j.gameserver.geoengine.pathfinding;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantLock;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.geoengine.geodata.ABlock;
import net.sf.l2j.gameserver.geoengine.geodata.GeoStructure;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.network.serverpackets.ExServerPrimitive;

public class NodeBuffer
{
	// Locking NodeBuffer to ensure thread-safe operations.
	private final ReentrantLock _lock = new ReentrantLock();
	
	// Container holding all available Nodes to be used.
	private final Node[] _buffer;
	private int _bufferIndex;
	// Container (binary-heap) holding Nodes to be explored.
	private PriorityQueue<Node> _opened;
	// Container holding Nodes already explored.
	private final List<Node> _closed;
	
	// Target coordinates.
	private int _gtx;
	private int _gty;
	private int _gtz;
	
	// Pathfinding statistics.
	private long _timeStamp;
	private long _lastElapsedTime;
	
	private Node _current;
	
	/**
	 * Constructor of NodeBuffer.
	 * @param size : The total size buffer. Determines the amount of {@link Node}s to be used for pathfinding.
	 */
	public NodeBuffer(int size)
	{
		// Create buffers based on given size.
		_buffer = new Node[size];
		_opened = new PriorityQueue<>(size);
		_closed = new ArrayList<>(size);
		
		// Create Nodes.
		for (int i = 0; i < size; i++)
			_buffer[i] = new Node();
	}
	
	/**
	 * Find path consisting of Nodes. Starts at origin coordinates, ends in target coordinates.
	 * @param gox : origin point x
	 * @param goy : origin point y
	 * @param goz : origin point z
	 * @param gtx : target point x
	 * @param gty : target point y
	 * @param gtz : target point z
	 * @return The list of {@link Location} for the path. Empty, if path not found.
	 */
	public final List<Location> findPath(int gox, int goy, int goz, int gtx, int gty, int gtz)
	{
		// Set start timestamp.
		_timeStamp = System.currentTimeMillis();
		
		// Set target coordinates.
		_gtx = gtx;
		_gty = gty;
		_gtz = gtz;
		
		// Get node from buffer.
		_current = _buffer[_bufferIndex++];
		
		// Set node geodata coordinates and movement cost.
		_current.setGeo(gox, goy, goz, GeoEngine.getInstance().getNsweNearest(gox, goy, goz));
		_current.setCost(null, 0, getCostH(gox, goy, goz));
		
		int count = 0;
		do
		{
			// Move node to closed list.
			_closed.add(_current);
			
			// Target reached, calculate path and return.
			if (_current.getGeoX() == _gtx && _current.getGeoY() == _gty && _current.getZ() == _gtz)
				return constructPath();
			
			// Expand current node.
			expand();
			
			// Get next node to expand.
			_current = _opened.poll();
		}
		while (_current != null && _bufferIndex < _buffer.length && ++count < Config.MAX_ITERATIONS);
		
		// Iteration failed, return empty path.
		return Collections.emptyList();
	}
	
	/**
	 * Build the path from subsequent nodes. Skip nodes in straight directions, keep only corner nodes.
	 * @return List of {@link Node}s representing the path.
	 */
	private List<Location> constructPath()
	{
		// Create result.
		LinkedList<Location> path = new LinkedList<>();
		
		// Clear X/Y direction.
		int dx = 0;
		int dy = 0;
		
		// Get parent node.
		Node parent = _current.getParent();
		
		// While parent exists.
		while (parent != null)
		{
			// Get parent node to current node X/Y direction.
			final int nx = parent.getGeoX() - _current.getGeoX();
			final int ny = parent.getGeoY() - _current.getGeoY();
			
			// Direction has changed?
			if (dx != nx || dy != ny)
			{
				// Add current node to the beginning of the path (Node must be cloned, as NodeBuffer reuses them).
				path.addFirst(_current.clone());
				
				// Update X/Y direction.
				dx = nx;
				dy = ny;
			}
			
			// Move current node and update its parent.
			_current = parent;
			parent = _current.getParent();
		}
		
		return path;
	}
	
	/**
	 * Creates list of Nodes to show debug path.
	 * @param debug : The debug packet to add debug informations in.
	 */
	public final void debugPath(ExServerPrimitive debug)
	{
		// Add all opened node as yellow points.
		for (Node n : _opened)
			debug.addPoint(String.valueOf(n.getCostF()), Color.YELLOW, true, n.getX(), n.getY(), n.getZ() - 16);
		
		// Add all opened node as blue points.
		for (Node n : _closed)
			debug.addPoint(String.valueOf(n.getCostF()), Color.BLUE, true, n.getX(), n.getY(), n.getZ() - 16);
	}
	
	public final boolean isLocked()
	{
		return _lock.tryLock();
	}
	
	public final void free()
	{
		_opened.clear();
		_closed.clear();
		
		for (int i = 0; i < _bufferIndex - 1; i++)
			_buffer[i].clean();
		_bufferIndex = 0;
		
		_current = null;
		
		_lastElapsedTime = System.currentTimeMillis() - _timeStamp;
		_lock.unlock();
	}
	
	public final long getElapsedTime()
	{
		return _lastElapsedTime;
	}
	
	/**
	 * Expand the current {@link Node} by exploring its neighbors (axially and diagonally).
	 */
	private void expand()
	{
		// Movement is blocked, skip.
		byte nswe = _current.getNSWE();
		if (nswe == GeoStructure.CELL_FLAG_NONE)
			return;
			
		// Get geo coordinates of the node to be expanded.
		// Note: Z coord shifted up to avoid dual-layer issues.
		final int x = _current.getGeoX();
		final int y = _current.getGeoY();
		final int z = _current.getZ() + GeoStructure.CELL_IGNORE_HEIGHT;
		
		byte nsweN = GeoStructure.CELL_FLAG_NONE;
		byte nsweS = GeoStructure.CELL_FLAG_NONE;
		byte nsweW = GeoStructure.CELL_FLAG_NONE;
		byte nsweE = GeoStructure.CELL_FLAG_NONE;
		
		// Can move north, expand.
		if ((nswe & GeoStructure.CELL_FLAG_N) != 0)
			nsweN = addNode(x, y - 1, z, Config.MOVE_WEIGHT);
		
		// Can move south, expand.
		if ((nswe & GeoStructure.CELL_FLAG_S) != 0)
			nsweS = addNode(x, y + 1, z, Config.MOVE_WEIGHT);
		
		// Can move west, expand.
		if ((nswe & GeoStructure.CELL_FLAG_W) != 0)
			nsweW = addNode(x - 1, y, z, Config.MOVE_WEIGHT);
		
		// Can move east, expand.
		if ((nswe & GeoStructure.CELL_FLAG_E) != 0)
			nsweE = addNode(x + 1, y, z, Config.MOVE_WEIGHT);
		
		// Can move north-west, expand.
		if ((nsweW & GeoStructure.CELL_FLAG_N) != 0 && (nsweN & GeoStructure.CELL_FLAG_W) != 0)
			addNode(x - 1, y - 1, z, Config.MOVE_WEIGHT_DIAG);
		
		// Can move north-east, expand.
		if ((nsweE & GeoStructure.CELL_FLAG_N) != 0 && (nsweN & GeoStructure.CELL_FLAG_E) != 0)
			addNode(x + 1, y - 1, z, Config.MOVE_WEIGHT_DIAG);
		
		// Can move south-west, expand.
		if ((nsweW & GeoStructure.CELL_FLAG_S) != 0 && (nsweS & GeoStructure.CELL_FLAG_W) != 0)
			addNode(x - 1, y + 1, z, Config.MOVE_WEIGHT_DIAG);
		
		// Can move south-east, expand.
		if ((nsweE & GeoStructure.CELL_FLAG_S) != 0 && (nsweS & GeoStructure.CELL_FLAG_E) != 0)
			addNode(x + 1, y + 1, z, Config.MOVE_WEIGHT_DIAG);
	}
	
	/**
	 * Take {@link Node} from buffer, validate it and add to opened list.
	 * @param gx : The new node X geodata coordinate.
	 * @param gy : The new node Y geodata coordinate.
	 * @param gz : The new node Z geodata coordinate.
	 * @param weight : The weight of movement to the new node.
	 * @return The nswe of the added node. Blank, if not added.
	 */
	private byte addNode(int gx, int gy, int gz, int weight)
	{
		// Check new node is out of geodata grid (world coordinates).
		if (gx < 0 || gx >= GeoStructure.GEO_CELLS_X || gy < 0 || gy >= GeoStructure.GEO_CELLS_Y)
			return GeoStructure.CELL_FLAG_NONE;
		
		// Check buffer has reached capacity.
		if (_bufferIndex >= _buffer.length)
			return GeoStructure.CELL_FLAG_NONE;
		
		// Get geodata block and check if there is a layer at given coordinates.
		ABlock block = GeoEngine.getInstance().getBlock(gx, gy);
		final int index = block.getIndexBelow(gx, gy, gz, null);
		if (index < 0)
			return GeoStructure.CELL_FLAG_NONE;
		
		// Get node geodata Z and nswe.
		gz = block.getHeight(index, null);
		final byte nswe = block.getNswe(index, null);
		
		// Get node from current index (don't move index yet).
		Node node = _buffer[_bufferIndex];
		
		// Node is nearby obstacle, override weight.
		if (nswe != GeoStructure.CELL_FLAG_ALL)
			weight = Config.OBSTACLE_WEIGHT;
		
		// Set node geodata coordinates.
		node.setGeo(gx, gy, gz, nswe);
		
		// Node is already added to opened list, return.
		if (_opened.contains(node))
			return nswe;
		
		// Node was already expanded, return.
		if (_closed.contains(node))
			return nswe;
		
		// The node is to be used. Set node movement cost and add it to opened list. Move the buffer index.
		node.setCost(_current, weight, getCostH(gx, gy, gz));
		_opened.add(node);
		_bufferIndex++;
		return nswe;
	}
	
	/**
	 * Calculate cost H value, calculated using diagonal distance method.<br>
	 * Note: Manhattan distance is too simple, causing to explore more unwanted cells.
	 * @param gx : The node geodata X coordinate.
	 * @param gy : The node geodata Y coordinate.
	 * @param gz : The node geodata Z coordinate.
	 * @return The cost H value (estimated cost to reach the target).
	 */
	private int getCostH(int gx, int gy, int gz)
	{
		// Get differences to the target.
		final int dx = Math.abs(gx - _gtx);
		final int dy = Math.abs(gy - _gty);
		final int dz = Math.abs(gz - _gtz) / GeoStructure.CELL_HEIGHT;
		
		// Get diagonal and axial differences to the target.
		final int dd = Math.min(dx, dy);
		final int da = Math.max(dx, dy) - dd;
		
		// Calculate the diagonal distance of the node to the target.
		return dd * Config.HEURISTIC_WEIGHT_DIAG + (da + dz) * Config.HEURISTIC_WEIGHT;
	}
}