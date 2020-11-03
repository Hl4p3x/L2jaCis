package net.sf.l2j.gameserver.network.serverpackets;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;

/**
 * A packet used to draw points and lines on client.<br/>
 * <b>Note:</b><br>
 * Names in points and lines are bugged they will appear even when not looking at them.<br>
 * Packet must be sent using sendTo() method, otherwise nested packets are not sent.
 */
public class ExServerPrimitive extends L2GameServerPacket
{
	private static final int MAX_SIZE = 16000;
	
	private final String _name;
	private final List<Point> _points = new ArrayList<>();
	private final List<Line> _lines = new ArrayList<>();
	private int _x;
	private int _y;
	private int _z;
	
	private final int _index;
	private int _size = 0;
	private ExServerPrimitive _next;
	
	/**
	 * @param name : An unique name this will be used to replace lines if second packet is sent.
	 * @param location : The {@link Player}'s enter world {@link Location}.
	 */
	public ExServerPrimitive(String name, Location location)
	{
		_name = name;
		_x = location.getX();
		_y = location.getY();
		_z = location.getZ();
		_index = 0;
	}
	
	/**
	 * Constructor of a dummy packet as a simple container. Should not be send out.
	 */
	public ExServerPrimitive()
	{
		_name = null;
		_x = 0;
		_y = 0;
		_z = 0;
		_index = -1;
	}
	
	/**
	 * Constructor of a following packet. Used, when parent packet is out of capacity.
	 * @param parent : A parent {@link ExServerPrimitive} packet used for nesting.
	 */
	public ExServerPrimitive(ExServerPrimitive parent)
	{
		_name = parent._name;
		_x = parent._x;
		_y = parent._y;
		_z = parent._z;
		_index = parent._index + 1;
	}
	
	/**
	 * Add point to the {@link ExServerPrimitive}, create next {@link ExServerPrimitive} if out of capacity.
	 * @param p : Added {@link Point}.
	 */
	private void addPoint(Point p)
	{
		// Check size and add point, if free capacity (or add if dummy packet).
		if (_size < MAX_SIZE || _index < 0)
		{
			_size += p.size();
			_points.add(p);
			return;
		}
		
		// Capacity not enough, create next packet (if needed).
		if (_next == null)
			_next = new ExServerPrimitive(this);
		
		// Add point to next packet.
		_next.addPoint(p);
	}
	
	/**
	 * Adds a point to be displayed on client.
	 * @param name the name that will be displayed over the point
	 * @param color the color
	 * @param isNameColored if {@code true} name will be colored as well.
	 * @param x the x coordinate for this point
	 * @param y the y coordinate for this point
	 * @param z the z coordinate for this point
	 */
	public void addPoint(String name, int color, boolean isNameColored, int x, int y, int z)
	{
		addPoint(new Point(name, color, isNameColored, x, y, z));
	}
	
	/**
	 * Adds a point to be displayed on client.
	 * @param name the name that will be displayed over the point
	 * @param color the color
	 * @param isNameColored if {@code true} name will be colored as well.
	 * @param location the Location to take coordinates for this point
	 */
	public void addPoint(String name, int color, boolean isNameColored, Location location)
	{
		addPoint(name, color, isNameColored, location.getX(), location.getY(), location.getZ());
	}
	
	/**
	 * Adds a point to be displayed on client.
	 * @param color the color
	 * @param x the x coordinate for this point
	 * @param y the y coordinate for this point
	 * @param z the z coordinate for this point
	 */
	public void addPoint(int color, int x, int y, int z)
	{
		addPoint("", color, false, x, y, z);
	}
	
	/**
	 * Adds a point to be displayed on client.
	 * @param color the color
	 * @param location the Location to take coordinates for this point
	 */
	public void addPoint(int color, Location location)
	{
		addPoint("", color, false, location);
	}
	
	/**
	 * Adds a point to be displayed on client.
	 * @param name the name that will be displayed over the point
	 * @param color the color
	 * @param isNameColored if {@code true} name will be colored as well.
	 * @param x the x coordinate for this point
	 * @param y the y coordinate for this point
	 * @param z the z coordinate for this point
	 */
	public void addPoint(String name, Color color, boolean isNameColored, int x, int y, int z)
	{
		addPoint(name, color.getRGB(), isNameColored, x, y, z);
	}
	
	/**
	 * Adds a point to be displayed on client.
	 * @param name the name that will be displayed over the point
	 * @param color the color
	 * @param isNameColored if {@code true} name will be colored as well.
	 * @param location the Location to take coordinates for this point
	 */
	public void addPoint(String name, Color color, boolean isNameColored, Location location)
	{
		addPoint(name, color.getRGB(), isNameColored, location);
	}
	
	/**
	 * Adds a point to be displayed on client.
	 * @param color the color
	 * @param x the x coordinate for this point
	 * @param y the y coordinate for this point
	 * @param z the z coordinate for this point
	 */
	public void addPoint(Color color, int x, int y, int z)
	{
		addPoint("", color, false, x, y, z);
	}
	
	/**
	 * Adds a point to be displayed on client.
	 * @param color the color
	 * @param location the Location to take coordinates for this point
	 */
	public void addPoint(Color color, Location location)
	{
		addPoint("", color, false, location);
	}
	
	/**
	 * Add line to the {@link ExServerPrimitive}, create next {@link ExServerPrimitive} if out of capacity.
	 * @param l : Added {@link Line}.
	 */
	private void addLine(Line l)
	{
		// Check size and add line, if free capacity (or add if dummy packet).
		if (_size < MAX_SIZE || _index < 0)
		{
			_size += l.size();
			_lines.add(l);
			return;
		}
		
		// Capacity not enough, create next packet (if needed).
		if (_next == null)
			_next = new ExServerPrimitive(this);
		
		// Add line to next packet.
		_next.addLine(l);
	}
	
	/**
	 * Adds a line to be displayed on client
	 * @param name the name that will be displayed over the middle of line
	 * @param color the color
	 * @param isNameColored if {@code true} name will be colored as well.
	 * @param x the x coordinate for this line start point
	 * @param y the y coordinate for this line start point
	 * @param z the z coordinate for this line start point
	 * @param x2 the x coordinate for this line end point
	 * @param y2 the y coordinate for this line end point
	 * @param z2 the z coordinate for this line end point
	 */
	public void addLine(String name, int color, boolean isNameColored, int x, int y, int z, int x2, int y2, int z2)
	{
		addLine(new Line(name, color, isNameColored, x, y, z, x2, y2, z2));
	}
	
	/**
	 * Adds a line to be displayed on client
	 * @param name the name that will be displayed over the middle of line
	 * @param color the color
	 * @param isNameColored if {@code true} name will be colored as well.
	 * @param location the Location to take coordinates for this line start point
	 * @param x2 the x coordinate for this line end point
	 * @param y2 the y coordinate for this line end point
	 * @param z2 the z coordinate for this line end point
	 */
	public void addLine(String name, int color, boolean isNameColored, Location location, int x2, int y2, int z2)
	{
		addLine(name, color, isNameColored, location.getX(), location.getY(), location.getZ(), x2, y2, z2);
	}
	
	/**
	 * Adds a line to be displayed on client
	 * @param name the name that will be displayed over the middle of line
	 * @param color the color
	 * @param isNameColored if {@code true} name will be colored as well.
	 * @param x the x coordinate for this line start point
	 * @param y the y coordinate for this line start point
	 * @param z the z coordinate for this line start point
	 * @param location the Location to take coordinates for this line end point
	 */
	public void addLine(String name, int color, boolean isNameColored, int x, int y, int z, Location location)
	{
		addLine(name, color, isNameColored, x, y, z, location.getX(), location.getY(), location.getZ());
	}
	
	/**
	 * Adds a line to be displayed on client
	 * @param name the name that will be displayed over the middle of line
	 * @param color the color
	 * @param isNameColored if {@code true} name will be colored as well.
	 * @param location the Location to take coordinates for this line start point
	 * @param location2 the Location to take coordinates for this line end point
	 */
	public void addLine(String name, int color, boolean isNameColored, Location location, Location location2)
	{
		addLine(name, color, isNameColored, location, location2.getX(), location2.getY(), location2.getZ());
	}
	
	/**
	 * Adds a line to be displayed on client
	 * @param color the color
	 * @param x the x coordinate for this line start point
	 * @param y the y coordinate for this line start point
	 * @param z the z coordinate for this line start point
	 * @param x2 the x coordinate for this line end point
	 * @param y2 the y coordinate for this line end point
	 * @param z2 the z coordinate for this line end point
	 */
	public void addLine(int color, int x, int y, int z, int x2, int y2, int z2)
	{
		addLine("", color, false, x, y, z, x2, y2, z2);
	}
	
	/**
	 * Adds a line to be displayed on client
	 * @param color the color
	 * @param location the Location to take coordinates for this line start point
	 * @param x2 the x coordinate for this line end point
	 * @param y2 the y coordinate for this line end point
	 * @param z2 the z coordinate for this line end point
	 */
	public void addLine(int color, Location location, int x2, int y2, int z2)
	{
		addLine("", color, false, location, x2, y2, z2);
	}
	
	/**
	 * Adds a line to be displayed on client
	 * @param color the color
	 * @param x the x coordinate for this line start point
	 * @param y the y coordinate for this line start point
	 * @param z the z coordinate for this line start point
	 * @param location the Location to take coordinates for this line end point
	 */
	public void addLine(int color, int x, int y, int z, Location location)
	{
		addLine("", color, false, x, y, z, location);
	}
	
	/**
	 * Adds a line to be displayed on client
	 * @param color the color
	 * @param location the Location to take coordinates for this line start point
	 * @param location2 the Location to take coordinates for this line end point
	 */
	public void addLine(int color, Location location, Location location2)
	{
		addLine("", color, false, location, location2);
	}
	
	/**
	 * Adds a line to be displayed on client
	 * @param name the name that will be displayed over the middle of line
	 * @param color the color
	 * @param isNameColored if {@code true} name will be colored as well.
	 * @param x the x coordinate for this line start point
	 * @param y the y coordinate for this line start point
	 * @param z the z coordinate for this line start point
	 * @param x2 the x coordinate for this line end point
	 * @param y2 the y coordinate for this line end point
	 * @param z2 the z coordinate for this line end point
	 */
	public void addLine(String name, Color color, boolean isNameColored, int x, int y, int z, int x2, int y2, int z2)
	{
		addLine(name, color.getRGB(), isNameColored, x, y, z, x2, y2, z2);
	}
	
	/**
	 * Adds a line to be displayed on client
	 * @param name the name that will be displayed over the middle of line
	 * @param color the color
	 * @param isNameColored if {@code true} name will be colored as well.
	 * @param location the Location to take coordinates for this line start point
	 * @param x2 the x coordinate for this line end point
	 * @param y2 the y coordinate for this line end point
	 * @param z2 the z coordinate for this line end point
	 */
	public void addLine(String name, Color color, boolean isNameColored, Location location, int x2, int y2, int z2)
	{
		addLine(name, color.getRGB(), isNameColored, location, x2, y2, z2);
	}
	
	/**
	 * Adds a line to be displayed on client
	 * @param name the name that will be displayed over the middle of line
	 * @param color the color
	 * @param isNameColored if {@code true} name will be colored as well.
	 * @param x the x coordinate for this line start point
	 * @param y the y coordinate for this line start point
	 * @param z the z coordinate for this line start point
	 * @param location the Location to take coordinates for this line end point
	 */
	public void addLine(String name, Color color, boolean isNameColored, int x, int y, int z, Location location)
	{
		addLine(name, color.getRGB(), isNameColored, x, y, z, location);
	}
	
	/**
	 * Adds a line to be displayed on client
	 * @param name the name that will be displayed over the middle of line
	 * @param color the color
	 * @param isNameColored if {@code true} name will be colored as well.
	 * @param location the Location to take coordinates for this line start point
	 * @param location2 the Location to take coordinates for this line end point
	 */
	public void addLine(String name, Color color, boolean isNameColored, Location location, Location location2)
	{
		addLine(name, color.getRGB(), isNameColored, location, location2);
	}
	
	/**
	 * Adds a line to be displayed on client
	 * @param color the color
	 * @param x the x coordinate for this line start point
	 * @param y the y coordinate for this line start point
	 * @param z the z coordinate for this line start point
	 * @param x2 the x coordinate for this line end point
	 * @param y2 the y coordinate for this line end point
	 * @param z2 the z coordinate for this line end point
	 */
	public void addLine(Color color, int x, int y, int z, int x2, int y2, int z2)
	{
		addLine("", color, false, x, y, z, x2, y2, z2);
	}
	
	/**
	 * Adds a line to be displayed on client
	 * @param color the color
	 * @param location the Location to take coordinates for this line start point
	 * @param x2 the x coordinate for this line end point
	 * @param y2 the y coordinate for this line end point
	 * @param z2 the z coordinate for this line end point
	 */
	public void addLine(Color color, Location location, int x2, int y2, int z2)
	{
		addLine("", color, false, location, x2, y2, z2);
	}
	
	/**
	 * Adds a line to be displayed on client
	 * @param color the color
	 * @param x the x coordinate for this line start point
	 * @param y the y coordinate for this line start point
	 * @param z the z coordinate for this line start point
	 * @param location the Location to take coordinates for this line end point
	 */
	public void addLine(Color color, int x, int y, int z, Location location)
	{
		addLine("", color, false, x, y, z, location);
	}
	
	/**
	 * Adds a line to be displayed on client
	 * @param color the color
	 * @param location the ILocational to take coordinates for this line start point
	 * @param location2 the ILocational to take coordinates for this line end point
	 */
	public void addLine(Color color, Location location, Location location2)
	{
		addLine("", color, false, location, location2);
	}
	
	/**
	 * Adds points and lines from existing {@link ExServerPrimitive} packet.
	 * @param esp : {@link ExServerPrimitive} packet to use points and lines from.
	 */
	public void addAll(ExServerPrimitive esp)
	{
		for (Point p : esp._points)
			addPoint(p);
		for (Line l : esp._lines)
			addLine(l);
	}
	
	/**
	 * Reset both lines and points {@link List}s.
	 */
	public void reset()
	{
		_lines.clear();
		_points.clear();
		_size = 0;
		
		if (_next != null)
			_next.reset();
	}
	
	/**
	 * Send packet to the {@link Player}. If out of capacity, send more packets.
	 * @param player : {@link Player} to send packet(s) to.
	 */
	public void sendTo(Player player)
	{
		// Packet is empty, add dummy points (happens at first packet only).
		if (_size == 0)
			addPoint(Color.WHITE, _x, _y, 16384);
		
		// Send packet.
		player.sendPacket(this);
		
		// No next packet, return.
		if (_next == null)
			return;
		
		// Check next packet.
		if (_next._size == 0)
		{
			// Next packet is empty, add dummy point.
			_next.addPoint(Color.WHITE, _x, _y, 16384);
			
			// Send packet and remove next packet.
			_next.sendTo(player);
			_next = null;
		}
		else
			// Next packet is not empty, send packet.
			_next.sendTo(player);
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xFE);
		writeH(0x24);
		if (_index == 0)
			writeS(_name);
		else
			writeS(_name == null ? "null" + _index : _name + _index);
		writeD(_x);
		writeD(_y);
		writeD(_z);
		writeD(Integer.MAX_VALUE); // has to do something with display range and angle
		writeD(Integer.MAX_VALUE); // has to do something with display range and angle
		
		writeD(_points.size() + _lines.size());
		
		for (Point point : _points)
		{
			writeC(1); // Its the type in this case Point
			writeS(point.getName());
			int color = point.getColor();
			writeD((color >> 16) & 0xFF); // R
			writeD((color >> 8) & 0xFF); // G
			writeD(color & 0xFF); // B
			writeD(point.isNameColored() ? 1 : 0);
			writeD(point.getX());
			writeD(point.getY());
			writeD(point.getZ());
		}
		
		for (Line line : _lines)
		{
			writeC(2); // Its the type in this case Line
			writeS(line.getName());
			int color = line.getColor();
			writeD((color >> 16) & 0xFF); // R
			writeD((color >> 8) & 0xFF); // G
			writeD(color & 0xFF); // B
			writeD(line.isNameColored() ? 1 : 0);
			writeD(line.getX());
			writeD(line.getY());
			writeD(line.getZ());
			writeD(line.getX2());
			writeD(line.getY2());
			writeD(line.getZ2());
		}
	}
	
	private static class Point
	{
		protected final String _name;
		private final int _color;
		private final boolean _isNameColored;
		private final int _x;
		private final int _y;
		private final int _z;
		
		public Point(String name, int color, boolean isNameColored, int x, int y, int z)
		{
			_name = name;
			_color = color;
			_isNameColored = isNameColored;
			_x = x;
			_y = y;
			_z = z;
		}
		
		/**
		 * @return the name
		 */
		public String getName()
		{
			return _name;
		}
		
		/**
		 * @return the color
		 */
		public int getColor()
		{
			return _color;
		}
		
		/**
		 * @return the isNameColored
		 */
		public boolean isNameColored()
		{
			return _isNameColored;
		}
		
		/**
		 * @return the x
		 */
		public int getX()
		{
			return _x;
		}
		
		/**
		 * @return the y
		 */
		public int getY()
		{
			return _y;
		}
		
		/**
		 * @return the z
		 */
		public int getZ()
		{
			return _z;
		}
		
		public int size()
		{
			// 1 byte, string (2 bytes per character + 2 termination bytes), 7 integers (4 bytes)
			return _name == null ? 31 : 31 + 2 * _name.length();
		}
	}
	
	private static class Line extends Point
	{
		private final int _x2;
		private final int _y2;
		private final int _z2;
		
		public Line(String name, int color, boolean isNameColored, int x, int y, int z, int x2, int y2, int z2)
		{
			super(name, color, isNameColored, x, y, z);
			_x2 = x2;
			_y2 = y2;
			_z2 = z2;
		}
		
		/**
		 * @return the x2
		 */
		public int getX2()
		{
			return _x2;
		}
		
		/**
		 * @return the y2
		 */
		public int getY2()
		{
			return _y2;
		}
		
		/**
		 * @return the z2
		 */
		public int getZ2()
		{
			return _z2;
		}
		
		@Override
		public int size()
		{
			// 1 byte, string (2 bytes per character + 2 termination bytes), 10 integers (4 bytes)
			return _name == null ? 43 : 43 + 2 * _name.length();
		}
	}
}