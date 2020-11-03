package net.sf.l2j.gameserver.handler.targethandlers;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetOwnerPet implements ITargetHandler
{
	@Override
	public Creature[] getTargetList(SkillUseHolder skillUseHolder)
	{
		final Creature caster = skillUseHolder.getCaster();
		final Creature target = caster.getActingPlayer();
		return new Creature[]
		{
			target
		};
	}
	
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.OWNER_PET;
	}
	
	@Override
	public Creature getFinalTarget(Creature target, Creature caster, L2Skill skill, boolean isCtrlPressed)
	{
		if (!(caster instanceof Summon))
			return null;
		
		final Creature owner = caster.getActingPlayer();
		if (owner == null || owner.isDead())
			return null;
		
		return owner;
	}
}