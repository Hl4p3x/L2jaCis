package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.awt.Color;
import java.util.List;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.geoengine.geodata.ABlock;
import net.sf.l2j.gameserver.geoengine.geodata.GeoStructure;
import net.sf.l2j.gameserver.geoengine.geodata.IGeoObject;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.location.SpawnLocation;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ExServerPrimitive;

public class AdminGeoEngine implements IAdminCommandHandler
{
	private final String Y = "x ";
	private final String N = "   ";
	
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_geo_bug",
		"admin_geo_pos",
		"admin_geo_see",
		"admin_geo_move",
		"admin_geo_fly",
		"admin_path_find",
		"admin_path_info",
	};
	
	@Override
	public boolean useAdminCommand(String command, Player activeChar)
	{
		if (command.startsWith("admin_geo_bug"))
		{
			final int geoX = GeoEngine.getGeoX(activeChar.getX());
			final int geoY = GeoEngine.getGeoY(activeChar.getY());
			if (GeoEngine.getInstance().hasGeoPos(geoX, geoY))
			{
				try
				{
					String comment = command.substring(14);
					if (GeoEngine.getInstance().addGeoBug(activeChar.getPosition(), activeChar.getName() + ": " + comment))
						activeChar.sendMessage("GeoData bug saved.");
				}
				catch (Exception e)
				{
					activeChar.sendMessage("Usage: //admin_geo_bug comments");
				}
			}
			else
				activeChar.sendMessage("There is no geodata at this position.");
		}
		else if (command.equals("admin_geo_pos"))
		{
			final int geoX = GeoEngine.getGeoX(activeChar.getX());
			final int geoY = GeoEngine.getGeoY(activeChar.getY());
			final int rx = (activeChar.getX() - World.WORLD_X_MIN) / World.TILE_SIZE + World.TILE_X_MIN;
			final int ry = (activeChar.getY() - World.WORLD_Y_MIN) / World.TILE_SIZE + World.TILE_Y_MIN;
			final ABlock block = GeoEngine.getInstance().getBlock(geoX, geoY);
			activeChar.sendMessage("Region: " + rx + "_" + ry + "; Block: " + block.getClass().getSimpleName());
			if (block.hasGeoPos())
			{
				// Block block = GeoData.getInstance().getBlock(geoX, geoY);
				final int geoZ = block.getHeightNearest(geoX, geoY, activeChar.getZ(), null);
				final byte nswe = block.getNsweNearest(geoX, geoY, geoZ, null);
				
				// activeChar.sendMessage("NSWE: " + block.getClass().getSimpleName());
				activeChar.sendMessage("    " + ((nswe & GeoStructure.CELL_FLAG_NW) != 0 ? Y : N) + ((nswe & GeoStructure.CELL_FLAG_N) != 0 ? Y : N) + ((nswe & GeoStructure.CELL_FLAG_NE) != 0 ? Y : N) + "         GeoX=" + geoX);
				activeChar.sendMessage("    " + ((nswe & GeoStructure.CELL_FLAG_W) != 0 ? Y : N) + "o " + ((nswe & GeoStructure.CELL_FLAG_E) != 0 ? Y : N) + "         GeoY=" + geoY);
				activeChar.sendMessage("    " + ((nswe & GeoStructure.CELL_FLAG_SW) != 0 ? Y : N) + ((nswe & GeoStructure.CELL_FLAG_S) != 0 ? Y : N) + ((nswe & GeoStructure.CELL_FLAG_SE) != 0 ? Y : N) + "         GeoZ=" + geoZ);
			}
			else
				activeChar.sendMessage("There is no geodata at this position.");
		}
		else if (command.equals("admin_geo_see"))
		{
			WorldObject target = activeChar.getTarget();
			if (target instanceof Creature)
			{
				ExServerPrimitive debug = activeChar.getDebugPacket("CAN_SEE");
				debug.reset();
				
				int ox = activeChar.getX();
				int oy = activeChar.getY();
				int oz = activeChar.getZ();
				int oh = (int) (2 * activeChar.getCollisionHeight());
				debug.addLine("origin", Color.BLUE, true, ox, oy, oz, ox, oy, oz + oh);
				oh = (oh * Config.PART_OF_CHARACTER_HEIGHT) / 100;
				
				Creature t = (Creature) target;
				int tx = t.getX();
				int ty = t.getY();
				int tz = t.getZ();
				int th = (int) (2 * t.getCollisionHeight());
				debug.addLine("target", Color.BLUE, true, tx, ty, tz, tx, ty, tz + th);
				th = (th * Config.PART_OF_CHARACTER_HEIGHT) / 100;
				
				IGeoObject ignore = (target instanceof IGeoObject) ? (IGeoObject) target : null;
				boolean canSee = GeoEngine.getInstance().canSee(ox, oy, oz, oh, tx, ty, tz, th, ignore, debug);
				
				oh += oz;
				th += tz;
				debug.addLine("Line-of-Sight", canSee ? Color.GREEN : Color.RED, true, ox, oy, oh, tx, ty, th);
				debug.addLine("Geodata limit", Color.MAGENTA, true, ox, oy, oh + Config.MAX_OBSTACLE_HEIGHT, tx, ty, th + Config.MAX_OBSTACLE_HEIGHT);
				
				debug.sendTo(activeChar);
			}
			else
				activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
		}
		else if (command.equals("admin_geo_move"))
		{
			WorldObject target = activeChar.getTarget();
			if (target != null)
			{
				SpawnLocation aLoc = activeChar.getPosition();
				SpawnLocation tLoc = target.getPosition();
				
				ExServerPrimitive debug = activeChar.getDebugPacket("CAN_MOVE");
				debug.reset();
				
				Location loc = GeoEngine.getInstance().getValidLocation(aLoc.getX(), aLoc.getY(), aLoc.getZ(), tLoc.getX(), tLoc.getY(), tLoc.getZ(), debug);
				debug.addLine("Can move", Color.GREEN, true, aLoc, loc);
				if (loc.getX() == tLoc.getX() && loc.getY() == tLoc.getY() && loc.getZ() == tLoc.getZ())
				{
					activeChar.sendMessage("Can move beeline.");
				}
				else
				{
					debug.addLine(Color.WHITE, aLoc, tLoc);
					debug.addLine("Inaccessible", Color.RED, true, loc, tLoc);
					debug.addPoint("Limit", Color.RED, true, loc);
					activeChar.sendMessage("Can not move beeline!");
				}
				
				debug.sendTo(activeChar);
			}
			else
				activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
		}
		else if (command.equals("admin_geo_fly"))
		{
			WorldObject target = activeChar.getTarget();
			if (target != null)
			{
				ExServerPrimitive debug = activeChar.getDebugPacket("CAN_FLY");
				debug.reset();
				
				int ox = activeChar.getX();
				int oy = activeChar.getY();
				int oz = activeChar.getZ();
				int oh = (int) (2 * activeChar.getCollisionHeight());
				debug.addLine("origin", Color.BLUE, true, ox, oy, oz - 32, ox, oy, oz + oh - 32);
				
				Creature t = (Creature) target;
				int tx = t.getX();
				int ty = t.getY();
				int tz = t.getZ();
				
				Location loc = GeoEngine.getInstance().getValidFloatLocation(ox, oy, oz, oh, tx, ty, tz, debug);
				int x = loc.getX();
				int y = loc.getY();
				int z = loc.getZ();
				
				boolean canFly = x == tx && y == ty && z == tz;
				
				debug.addLine("Can fly", Color.GREEN, true, ox, oy, oz - 32, x, y, z - 32);
				if (canFly)
				{
					activeChar.sendMessage("Can fly beeline.");
				}
				else
				{
					debug.addLine(Color.WHITE, ox, oy, oz - 32, tx, ty, tz - 32);
					debug.addLine("Inaccessible", Color.RED, true, x, y, z - 32, tx, ty, tz - 32);
					debug.addLine("Last position", Color.RED, true, x, y, z - 32, x, y, z + oh - 32);
					activeChar.sendMessage("Can not fly beeline!");
				}
				
				debug.addLine("Line-of-Flight MIN", canFly ? Color.GREEN : Color.RED, true, ox, oy, oz - 32, tx, ty, tz - 32);
				debug.addLine("Line-of-Fligth MAX", canFly ? Color.GREEN : Color.RED, true, ox, oy, oz + oh - 32, tx, ty, tz + oh - 32);
				
				debug.sendTo(activeChar);
			}
			else
				activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
		}
		else if (command.equals("admin_path_find"))
		{
			if (activeChar.getTarget() != null)
			{
				ExServerPrimitive debug = activeChar.getDebugPacket("PATH");
				debug.reset();
				
				List<Location> path = GeoEngine.getInstance().findPath(activeChar.getX(), activeChar.getY(), (short) activeChar.getZ(), activeChar.getTarget().getX(), activeChar.getTarget().getY(), (short) activeChar.getTarget().getZ(), true, debug);
				if (path == null)
					activeChar.sendMessage("No route found or pathfinding disabled.");
				else
					for (Location point : path)
						activeChar.sendMessage("x:" + point.getX() + " y:" + point.getY() + " z:" + point.getZ());
					
				debug.sendTo(activeChar);
			}
			else
				activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
		}
		else if (command.equals("admin_path_info"))
		{
			final List<String> info = GeoEngine.getInstance().getStat();
			if (info == null)
				activeChar.sendMessage("Pathfinding disabled.");
			else
				for (String msg : info)
				{
					System.out.println(msg);
					activeChar.sendMessage(msg);
				}
		}
		else
			return false;
		
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}