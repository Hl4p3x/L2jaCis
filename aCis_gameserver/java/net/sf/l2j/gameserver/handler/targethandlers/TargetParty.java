package net.sf.l2j.gameserver.handler.targethandlers;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.group.Party;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetParty implements ITargetHandler
{
	@Override
	public Creature[] getTargetList(SkillUseHolder skillUseHolder)
	{
		final L2Skill skill = skillUseHolder.getSkill();
		final Creature caster = skillUseHolder.getCaster();
		final List<Creature> list = new ArrayList<>();
		
		list.add(caster);
		
		final Player player = caster.getActingPlayer();
		if (caster instanceof Summon)
		{
			if (L2Skill.addCharacter(caster, player, skill.getSkillRadius(), false))
				list.add(player);
		}
		else if (caster instanceof Player)
		{
			if (L2Skill.addSummon(caster, player, skill.getSkillRadius(), false))
				list.add(player.getSummon());
		}
		
		final Party party = caster.getParty();
		if (party != null)
		{
			for (Player member : party.getMembers())
			{
				if (member == player)
					continue;
				
				if (L2Skill.addCharacter(caster, member, skill.getSkillRadius(), false))
					list.add(member);
				
				if (L2Skill.addSummon(caster, member, skill.getSkillRadius(), false))
					list.add(member.getSummon());
			}
		}
		
		return list.toArray(new Creature[list.size()]);
	}
	
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.PARTY;
	}
	
	@Override
	public Creature getFinalTarget(Creature target, Creature caster, L2Skill skill, boolean isCtrlPressed)
	{
		return caster;
	}
}