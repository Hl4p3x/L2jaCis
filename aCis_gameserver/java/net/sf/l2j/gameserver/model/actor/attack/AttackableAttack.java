package net.sf.l2j.gameserver.model.actor.attack;

import net.sf.l2j.gameserver.model.actor.Creature;

/**
 * This class groups all attack data related to a {@link Creature}.
 */
public class AttackableAttack extends CreatureAttack
{
	public AttackableAttack(Creature creature)
	{
		super(creature);
	}
	
	@Override
	public boolean canDoAttack(Creature target)
	{
		if (!super.canDoAttack(target))
			return false;
		
		if (target.isFakeDeath())
			return false;
		
		// TODO2 check if needed
		if (_isBow && !isBowAttackReused())
			return false;
		
		return true;
	}
}