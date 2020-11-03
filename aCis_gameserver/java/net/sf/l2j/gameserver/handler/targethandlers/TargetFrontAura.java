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
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetFrontAura implements ITargetHandler
{
	@Override
	public Creature[] getTargetList(SkillUseHolder skillUseHolder)
	{
		final L2Skill skill = skillUseHolder.getSkill();
		final Creature caster = skillUseHolder.getCaster();
		final Boolean isCtrlPressed = skillUseHolder.isCtrlPressed();
		final List<Creature> list = new ArrayList<>();
		if (skill.getSkillType() == SkillType.DUMMY)
		{
			final Player sourcePlayer = caster.getActingPlayer();
			list.add(caster);
			
			for (Creature creature : caster.getKnownTypeInRadius(Creature.class, skill.getSkillRadius()))
			{
				if (!(creature == caster || creature == sourcePlayer || creature instanceof Npc || creature instanceof Attackable))
					continue;
				
				list.add(creature);
			}
		}
		else
		{
			final boolean srcInArena = caster.isInArena();
			
			for (Creature creature : caster.getKnownTypeInRadius(Creature.class, skill.getSkillRadius()))
			{
				if (creature instanceof Attackable || creature instanceof Playable)
				{
					if (!creature.isInFrontOf(caster))
						continue;
					
					if (!L2Skill.checkForAreaOffensiveSkills(caster, creature, skill, isCtrlPressed, srcInArena))
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
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.FRONT_AURA;
	}
	
	@Override
	public Creature getFinalTarget(Creature target, Creature caster, L2Skill skill, boolean isCtrlPressed)
	{
		return caster;
	}
}