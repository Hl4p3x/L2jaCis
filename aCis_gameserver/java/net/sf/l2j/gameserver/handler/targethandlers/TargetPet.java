package net.sf.l2j.gameserver.handler.targethandlers;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetPet implements ITargetHandler
{
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.PET;
	}
	
	@Override
	public Creature[] getTargetList(Creature caster, Creature target, L2Skill skill)
	{
		return new Creature[]
		{
			target
		};
	}
	
	@Override
	public Creature getFinalTarget(Creature caster, Creature target, L2Skill skill)
	{
		final Creature summon = caster.getSummon();
		if (summon == null || summon.isDead())
			return null;
		
		return summon;
	}
}