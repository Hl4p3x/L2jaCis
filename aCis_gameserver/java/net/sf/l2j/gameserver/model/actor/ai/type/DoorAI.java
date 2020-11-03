package net.sf.l2j.gameserver.model.actor.ai.type;

import net.sf.l2j.gameserver.model.actor.Boat;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.instance.Door;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.location.SpawnLocation;

public class DoorAI extends CreatureAI
{
	public DoorAI(Door door)
	{
		super(door);
	}
	
	@Override
	protected void onIntentionIdle()
	{
	}
	
	@Override
	protected void onIntentionActive()
	{
	}
	
	@Override
	protected void onIntentionAttack(Creature target, boolean isShiftPressed)
	{
	}
	
	@Override
	protected void onIntentionCast(SkillUseHolder skillUseHolder, ItemInstance itemInstance)
	{
	}
	
	@Override
	protected void onIntentionMoveTo(Location loc, Boat boat)
	{
	}
	
	@Override
	public void onEvtAttacked(Creature attacker)
	{
	}
	
	@Override
	protected void onEvtFinishedAttack()
	{
	}
	
	@Override
	protected void onEvtArrived()
	{
	}
	
	@Override
	protected void onEvtArrivedBlocked(SpawnLocation loc)
	{
	}
	
	@Override
	protected void onEvtDead()
	{
	}
}