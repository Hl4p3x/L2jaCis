package net.sf.l2j.gameserver.model.actor.instance;

import net.sf.l2j.gameserver.enums.IntentionType;
import net.sf.l2j.gameserver.enums.SiegeSide;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.ai.type.CreatureAI;
import net.sf.l2j.gameserver.model.actor.ai.type.SiegeGuardAI;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;

/**
 * This class represents all guards in the world.
 */
public final class SiegeGuard extends Attackable
{
	public SiegeGuard(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public CreatureAI getAI()
	{
		CreatureAI ai = _ai;
		if (ai == null)
		{
			synchronized (this)
			{
				ai = _ai;
				if (ai == null)
					_ai = ai = new SiegeGuardAI(this);
			}
		}
		return ai;
	}
	
	@Override
	public boolean isAttackableBy(Creature attacker)
	{
		if (!super.isAttackableBy(attacker))
			return false;
		
		if (getCastle() == null)
			return false;
		
		if (!getCastle().getSiege().isInProgress())
			return false;
		
		if (getCastle().getSiege().checkSides(attacker.getActingPlayer().getClan(), SiegeSide.DEFENDER, SiegeSide.OWNER))
			return false;
		
		return true;
	}
	
	@Override
	public boolean hasRandomAnimation()
	{
		return false;
	}
	
	@Override
	public void addDamageHate(Creature attacker, int damage, int aggro)
	{
		// Can't add friendly Guard as attacker.
		if (attacker instanceof SiegeGuard)
			return;
		
		super.addDamageHate(attacker, damage, aggro);
	}
	
	@Override
	public void reduceHate(Creature target, int amount)
	{
		// TODO amount is not taken into consideration
		stopHating(target);
		setTarget(null);
		getAI().tryTo(IntentionType.ACTIVE, null, null);
	}
	
	/**
	 * @return true if the {@link Attackable} successfully returned to spawn point. In case of minions, they are simply deleted.
	 */
	@Override
	public boolean returnHome()
	{
		// TODO Is this necessary?
		if (isDead())
			return false;
		
		// TODO is getSpawn() necessary?
		if (getSpawn() != null && !isIn2DRadius(getSpawn().getLoc(), getDriftRange()))
		{
			cleanAllHate();
			
			setIsReturningToSpawnPoint(true);
			forceRunStance();
			getAI().tryTo(IntentionType.MOVE_TO, getSpawn().getLoc(), null);
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean isGuard()
	{
		return true;
	}
	
	@Override
	public int getDriftRange()
	{
		return 20;
	}
}