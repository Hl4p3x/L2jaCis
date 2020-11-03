package net.sf.l2j.gameserver.handler.targethandlers;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetGround implements ITargetHandler
{
	@Override
	public Creature[] getTargetList(SkillUseHolder skillUseHolder)
	{
		return EMPTY_TARGET_ARRAY;
	}
	
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.GROUND;
	}
	
	@Override
	public Creature getFinalTarget(Creature target, Creature caster, L2Skill skill, boolean isCtrlPressed)
	{
		return caster;
	}
}