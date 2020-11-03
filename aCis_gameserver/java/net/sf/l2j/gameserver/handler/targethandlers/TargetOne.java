package net.sf.l2j.gameserver.handler.targethandlers;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetOne implements ITargetHandler
{
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.ONE;
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
		if (target == null || target.isDead())
			return null;
		
		boolean canTargetSelf = false;
		switch (skill.getSkillType())
		{
			case BUFF:
			case HEAL:
			case HEAL_PERCENT:
			case NEGATE:
			case CANCEL_DEBUFF:
			case REFLECT:
			case COMBATPOINTHEAL:
			case CONT:
				canTargetSelf = true;
				break;
		}
		
		if (target == caster && !canTargetSelf)
			return null;
		
		return target;
	}
}