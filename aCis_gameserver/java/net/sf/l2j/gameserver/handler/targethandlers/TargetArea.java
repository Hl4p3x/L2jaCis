package net.sf.l2j.gameserver.handler.targethandlers;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetArea implements ITargetHandler
{
	@Override
	public Creature[] getTargetList(SkillUseHolder skillUseHolder)
	{
		final L2Skill skill = skillUseHolder.getSkill();
		final Creature target = skillUseHolder.getFinalTarget();
		final Creature caster = skillUseHolder.getCaster();
		final Boolean isCtrlPressed = skillUseHolder.isCtrlPressed();
		final boolean srcInArena = caster.isInArena();
		final List<Creature> list = new ArrayList<>();
		list.add(target);
		
		for (Creature creature : target.getKnownTypeInRadius(Creature.class, skill.getSkillRadius()))
		{
			if (creature == target)
				continue;
			
			if (!(creature instanceof Attackable || creature instanceof Playable))
				continue;
			
			if (!L2Skill.checkForAreaOffensiveSkills(caster, creature, skill, isCtrlPressed, srcInArena))
				continue;
			
			list.add(creature);
		}
		
		if (list.isEmpty())
			return EMPTY_TARGET_ARRAY;
		
		return list.toArray(new Creature[list.size()]);
	}
	
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.AREA;
	}
	
	@Override
	public Creature getFinalTarget(Creature target, Creature caster, L2Skill skill, boolean isCtrlPressed)
	{
		if (target == null || target == caster || target.isDead())
			return null;
		
		return target;
	}
}