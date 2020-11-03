package net.sf.l2j.gameserver.handler.targethandlers;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetGround implements ITargetHandler
{
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.GROUND;
	}
	
	@Override
	public Creature[] getTargetList(Creature caster, Creature target, L2Skill skill)
	{
		return EMPTY_TARGET_ARRAY;
	}
	
	@Override
	public Creature getFinalTarget(Creature caster, Creature target, L2Skill skill)
	{
		return caster;
	}
}