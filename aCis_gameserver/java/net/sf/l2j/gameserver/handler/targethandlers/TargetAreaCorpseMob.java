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

public class TargetAreaCorpseMob implements ITargetHandler
{
	@Override
	public Creature[] getTargetList(SkillUseHolder skillUseHolder)
	{
		final Creature target = skillUseHolder.getFinalTarget();
		final List<Creature> list = new ArrayList<>();
		list.add(target);
		
		final L2Skill skill = skillUseHolder.getSkill();
		final Creature caster = skillUseHolder.getCaster();
		final Boolean isCtrlPressed = skillUseHolder.isCtrlPressed();
		final boolean srcInArena = caster.isInArena();
		for (Creature creature : target.getKnownTypeInRadius(Creature.class, skill.getSkillRadius()))
		{
			if (!(creature instanceof Attackable || creature instanceof Playable))
				continue;
			
			if (!L2Skill.checkForAreaOffensiveSkills(caster, creature, skill, isCtrlPressed, srcInArena))
				continue;
			
			list.add(creature);
		}
		
		return list.toArray(new Creature[list.size()]);
	}
	
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.AREA_CORPSE_MOB;
	}
	
	@Override
	public Creature getFinalTarget(Creature target, Creature caster, L2Skill skill, boolean isCtrlPressed)
	{
		if ((!(target instanceof Attackable)) || !target.isDead())
			return null;
		
		return target;
	}
}