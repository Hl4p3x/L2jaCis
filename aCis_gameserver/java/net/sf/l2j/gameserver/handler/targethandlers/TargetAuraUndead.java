package net.sf.l2j.gameserver.handler.targethandlers;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.Servitor;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetAuraUndead implements ITargetHandler
{
	@Override
	public Creature[] getTargetList(SkillUseHolder skillUseHolder)
	{
		final L2Skill skill = skillUseHolder.getSkill();
		final Creature caster = skillUseHolder.getCaster();
		final Boolean isCtrlPressed = skillUseHolder.isCtrlPressed();
		final boolean srcInArena = caster.isInArena();
		final List<Creature> list = new ArrayList<>();
		for (Creature creature : caster.getKnownTypeInRadius(Creature.class, skill.getSkillRadius()))
		{
			if (!(creature instanceof Npc) && !(creature instanceof Servitor))
				continue;
			
			if (creature.isDead() || !creature.isUndead())
				continue;
			
			if (!L2Skill.checkForAreaOffensiveSkills(caster, creature, skill, isCtrlPressed, srcInArena))
				continue;
			
			if (!GeoEngine.getInstance().canSeeTarget(caster, creature))
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
		return SkillTargetType.AURA_UNDEAD;
	}
	
	@Override
	public Creature getFinalTarget(Creature target, Creature caster, L2Skill skill, boolean isCtrlPressed)
	{
		return caster;
	}
}