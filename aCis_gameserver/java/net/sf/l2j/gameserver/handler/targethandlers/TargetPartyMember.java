package net.sf.l2j.gameserver.handler.targethandlers;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetPartyMember implements ITargetHandler
{
	@Override
	public Creature[] getTargetList(SkillUseHolder skillUseHolder)
	{
		// Single target skill. Will never be called.
		return EMPTY_TARGET_ARRAY;
	}
	
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.PARTY_MEMBER;
	}
	
	@Override
	public Creature getFinalTarget(Creature target, Creature caster, L2Skill skill, boolean isCtrlPressed)
	{
		if (!(target != null && (target == caster || (caster.isInParty() && target.isInParty() && caster.getParty().getLeaderObjectId() == target.getParty().getLeaderObjectId()) || (caster instanceof Player && target instanceof Summon && caster.getSummon() == target) || (caster instanceof Summon && target instanceof Player && caster == target.getSummon()))))
			return null;
		
		if (target.isDead())
			return null;
		
		return target;
	}
}