package net.sf.l2j.gameserver.handler.targethandlers;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetOwnerPet implements ITargetHandler
{
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.OWNER_PET;
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
		if (!(caster instanceof Summon))
			return null;
		
		final Creature owner = caster.getActingPlayer();
		if (owner == null || owner.isDead())
			return null;
		
		return owner;
	}
}