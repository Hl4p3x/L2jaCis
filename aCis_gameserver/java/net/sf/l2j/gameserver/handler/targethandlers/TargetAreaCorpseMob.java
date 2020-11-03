package net.sf.l2j.gameserver.handler.targethandlers;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetAreaCorpseMob implements ITargetHandler
{
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.AREA_CORPSE_MOB;
	}
	
	@Override
	public Creature[] getTargetList(Creature caster, Creature target, L2Skill skill)
	{
		final List<Creature> list = new ArrayList<>();
		list.add(target);
		
		for (Creature creature : target.getKnownTypeInRadius(Creature.class, skill.getSkillRadius()))
		{
			if (!(creature instanceof Attackable || creature instanceof Playable))
				continue;
			
			if (!L2Skill.checkForAreaOffensiveSkills(caster, creature, skill, false, false))
				continue;
			
			list.add(creature);
		}
		
		return list.toArray(new Creature[list.size()]);
	}
	
	@Override
	public Creature getFinalTarget(Creature caster, Creature target, L2Skill skill)
	{
		if ((!(target instanceof Attackable)) || !target.isDead())
			return null;
		
		return target;
	}
}