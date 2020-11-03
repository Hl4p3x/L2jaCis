package net.sf.l2j.gameserver.model.actor.instance;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.gameserver.enums.SiegeSide;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.entity.Siege;
import net.sf.l2j.gameserver.model.spawn.Spawn;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;

public class ControlTower extends Npc
{
	private final List<Spawn> _guards = new ArrayList<>();
	private boolean _isActive = true;
	
	public ControlTower(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public boolean isAttackableBy(Creature attacker)
	{
		if (!super.isAttackableBy(attacker))
			return false;
		
		// TODO Summons can't damage?
		if (!(attacker instanceof Player))
			return false;
		
		if (getCastle() == null)
			return false;
		
		if (!getCastle().getSiege().isInProgress())
			return false;
		
		if (!getCastle().getSiege().checkSides(attacker.getActingPlayer().getClan(), SiegeSide.ATTACKER))
			return false;
		
		return true;
	}
	
	@Override
	public void onInteract(Player player)
	{
	}
	
	@Override
	public boolean doDie(Creature killer)
	{
		if (getCastle() != null)
		{
			final Siege siege = getCastle().getSiege();
			if (siege.isInProgress())
			{
				_isActive = false;
				
				for (Spawn spawn : _guards)
					spawn.setRespawnState(false);
				
				_guards.clear();
				
				// If siege life controls reach 0, broadcast a message to defenders.
				if (siege.getControlTowerCount() == 0)
					siege.announceToPlayers(SystemMessage.getSystemMessage(SystemMessageId.TOWER_DESTROYED_NO_RESURRECTION), false);
				
				// Spawn a little version of it. This version is a simple NPC, cleaned on siege end.
				try
				{
					final Spawn spawn = new Spawn(13003);
					spawn.setLoc(getPosition());
					
					final Npc tower = spawn.doSpawn(false);
					tower.setCastle(getCastle());
					
					siege.getDestroyedTowers().add(tower);
				}
				catch (Exception e)
				{
					LOGGER.error("Couldn't spawn the control tower.", e);
				}
			}
		}
		return super.doDie(killer);
	}
	
	@Override
	public boolean hasRandomAnimation()
	{
		return false;
	}
	
	public void registerGuard(Spawn guard)
	{
		_guards.add(guard);
	}
	
	public final List<Spawn> getGuards()
	{
		return _guards;
	}
	
	public final boolean isActive()
	{
		return _isActive;
	}
}