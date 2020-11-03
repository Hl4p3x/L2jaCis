package net.sf.l2j.gameserver.model.actor.cast;

import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Summon;

/**
 * This class groups all cast data related to a {@link Summon}.
 */
public class SummonCast extends PlayableCast
{
	public SummonCast(Creature creature)
	{
		super(creature);
	}
}