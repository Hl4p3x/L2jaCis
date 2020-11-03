package net.sf.l2j.gameserver.model.actor.instance;

import net.sf.l2j.gameserver.enums.IntentionType;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.ShowTownMap;
import net.sf.l2j.gameserver.network.serverpackets.StaticObjectInfo;

/**
 * A static object with low amount of interactions and no AI - such as throne, village town maps, etc.
 */
public class StaticObject extends WorldObject
{
	private int _staticObjectId;
	private int _type = -1; // 0 - map signs, 1 - throne , 2 - arena signs
	private boolean _isBusy; // True - if someone sitting on the throne
	private ShowTownMap _map;
	
	public StaticObject(int objectId)
	{
		super(objectId);
	}
	
	/**
	 * @return the StaticObjectId.
	 */
	public int getStaticObjectId()
	{
		return _staticObjectId;
	}
	
	/**
	 * @param StaticObjectId The StaticObjectId to set.
	 */
	public void setStaticObjectId(int StaticObjectId)
	{
		_staticObjectId = StaticObjectId;
	}
	
	public int getType()
	{
		return _type;
	}
	
	public void setType(int type)
	{
		_type = type;
	}
	
	public boolean isBusy()
	{
		return _isBusy;
	}
	
	public void setBusy(boolean busy)
	{
		_isBusy = busy;
	}
	
	public void setMap(String texture, int x, int y)
	{
		_map = new ShowTownMap("town_map." + texture, x, y);
	}
	
	public ShowTownMap getMap()
	{
		return _map;
	}
	
	@Override
	public void onInteract(Player player)
	{
		if (getType() == 2)
		{
			final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			html.setFile("data/html/signboard.htm");
			player.sendPacket(html);
		}
		else if (getType() == 0)
			player.sendPacket(getMap());
	}
	
	@Override
	public void onAction(Creature target, boolean isCtrlPressed, boolean isShiftPressed)
	{
		Player player = (Player) target;
		if (player.isGM() && isShiftPressed)
		{
			final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			html.setFile("data/html/admin/staticinfo.htm");
			html.replace("%x%", getX());
			html.replace("%y%", getY());
			html.replace("%z%", getZ());
			html.replace("%objid%", getObjectId());
			html.replace("%staticid%", getStaticObjectId());
			html.replace("%class%", getClass().getSimpleName());
			player.sendPacket(html);
			
			if (player.getTarget() != this)
				player.setTarget(this);
			
			return;
		}
		
		if (player.getTarget() != this)
			player.setTarget(this);
		else
			player.getAI().tryTo(IntentionType.INTERACT, this, isShiftPressed);
	}
	
	@Override
	public void sendInfo(Player player)
	{
		player.sendPacket(new StaticObjectInfo(this));
	}
}