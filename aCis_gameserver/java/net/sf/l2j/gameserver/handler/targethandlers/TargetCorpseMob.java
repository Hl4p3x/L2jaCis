package net.sf.l2j.gameserver.handler.targethandlers;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.enums.skills.SkillType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.skills.L2Skill;
import net.sf.l2j.gameserver.taskmanager.DecayTaskManager;

public class TargetCorpseMob implements ITargetHandler
{
	@Override
	public Creature[] getTargetList(SkillUseHolder skillUseHolder)
	{
		final Creature target = skillUseHolder.getFinalTarget();
		return new Creature[]
		{
			target
		};
	}
	
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.CORPSE_MOB;
	}
	
	@Override
	public Creature getFinalTarget(Creature target, Creature caster, L2Skill skill, boolean isCtrlPressed)
	{
		if (!(target instanceof Attackable) || !target.isDead())
			return null;
		
		// TODO Needs SkillHandler to display proper error message. Id 1247 CORPSE_TOO_OLD_SKILL_NOT_USED
		if (skill.getSkillType() == SkillType.DRAIN && !DecayTaskManager.getInstance().isCorpseActionAllowed((Monster) target))
			return null;
		
		return target;
	}
}