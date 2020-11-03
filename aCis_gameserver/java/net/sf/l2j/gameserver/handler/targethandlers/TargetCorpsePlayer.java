package net.sf.l2j.gameserver.handler.targethandlers;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.Pet;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetCorpsePlayer implements ITargetHandler
{
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.CORPSE_PLAYER;
	}
	
	@Override
	public Creature[] getTargetList(Creature caster, Creature target, L2Skill skill)
	{
		// Single target skill. Will never be called.
		return EMPTY_TARGET_ARRAY;
	}
	
	@Override
	public Creature getFinalTarget(Creature caster, Creature target, L2Skill skill)
	{
		if (!(target instanceof Player) && !(target instanceof Pet))
			return null;
		
		if (!target.isDead())
			return null;
		
		return target;
	}
}