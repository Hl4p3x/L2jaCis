package net.sf.l2j.gameserver.handler.targethandlers;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.instance.Servitor;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetSummon implements ITargetHandler
{
	@Override
	public Creature[] getTargetList(SkillUseHolder skillUseHolder)
	{
		final Creature caster = skillUseHolder.getCaster();
		final Creature target = caster.getSummon();
		return new Creature[]
		{
			target
		};
	}
	
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.SUMMON;
	}
	
	@Override
	public Creature getFinalTarget(Creature target, Creature caster, L2Skill skill, boolean isCtrlPressed)
	{
		final Creature summon = caster.getSummon();
		if (!(summon instanceof Servitor) || summon.isDead())
			return null;
		
		return summon;
	}
	
}