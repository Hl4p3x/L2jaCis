package net.sf.l2j.gameserver.handler.targethandlers;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.enums.skills.SkillType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetBehindAura implements ITargetHandler
{
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.BEHIND_AURA;
	}
	
	@Override
	public Creature[] getTargetList(Creature caster, Creature target, L2Skill skill)
	{
		final List<Creature> list = new ArrayList<>();
		if (skill.getSkillType() == SkillType.DUMMY)
		{
			list.add(caster);
			
			final Player sourcePlayer = caster.getActingPlayer();
			for (Creature creature : caster.getKnownTypeInRadius(Creature.class, skill.getSkillRadius()))
			{
				if (!(creature == caster || creature == sourcePlayer || creature instanceof Npc || creature instanceof Attackable))
					continue;
				
				list.add(creature);
			}
		}
		else
		{
			for (Creature creature : caster.getKnownTypeInRadius(Creature.class, skill.getSkillRadius()))
			{
				if (creature instanceof Attackable || creature instanceof Playable)
				{
					if (!creature.isBehind(caster))
						continue;
					
					if (!L2Skill.checkForAreaOffensiveSkills(caster, creature, skill, false, false))
						continue;
					
					list.add(creature);
				}
			}
		}
		
		if (list.isEmpty())
			return EMPTY_TARGET_ARRAY;
		
		return list.toArray(new Creature[list.size()]);
	}
	
	@Override
	public Creature getFinalTarget(Creature caster, Creature target, L2Skill skill)
	{
		return caster;
	}
}