package net.sf.l2j.gameserver.handler.targethandlers;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetPartyOther implements ITargetHandler
{
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.PARTY_OTHER;
	}
	
	@Override
	public Creature[] getTargetList(Creature caster, Creature target, L2Skill skill)
	{
		// Single target skill. Will never be called.
		return EMPTY_TARGET_ARRAY;
	}
	
	@Override
	public Creature getFinalTarget(Creature caster, Creature target, L2Skill skill)
	{
		if (!(target instanceof Player) || target.isDead())
			return null;
		
		if (!(target != caster && caster.isInParty() && target.isInParty() && caster.getParty().getLeaderObjectId() == target.getParty().getLeaderObjectId()))
			return null;
		
		if (skill.getId() == 426 && ((Player) target).isMageClass())
			return null;
		
		if (skill.getId() == 427 && !((Player) target).isMageClass())
			return null;
		
		return target;
	}
}