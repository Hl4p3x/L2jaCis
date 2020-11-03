package net.sf.l2j.gameserver.handler.targethandlers;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetAlly implements ITargetHandler
{
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.ALLY;
	}
	
	@Override
	public Creature[] getTargetList(Creature caster, Creature target, L2Skill skill)
	{
		final Player player = caster.getActingPlayer();
		
		if (player.isInOlympiadMode())
			return new Creature[]
			{
				caster
			};
		
		final List<Creature> list = new ArrayList<>();
		list.add(player);
		
		if (L2Skill.addSummon(caster, player, skill.getSkillRadius(), false))
			list.add(player.getSummon());
		
		if (player.getClan() != null)
		{
			for (Player obj : caster.getKnownTypeInRadius(Player.class, skill.getSkillRadius()))
			{
				if (obj.isDead())
					continue;
				
				// Only buff allies
				if ((obj.getAllyId() == 0 || obj.getAllyId() != player.getAllyId()) && (obj.getClan() == null || obj.getClanId() != player.getClanId()))
					continue;
				
				// Do not buff opposing duel side
				if (player.isInDuel())
				{
					if (player.getDuelId() != obj.getDuelId())
						continue;
					
					if (player.isInParty() && obj.isInParty() && player.getParty().getLeaderObjectId() != obj.getParty().getLeaderObjectId())
						continue;
				}
				
				final Summon summon = obj.getSummon();
				if (summon != null && !summon.isDead())
					list.add(summon);
				
				list.add(obj);
			}
		}
		
		return list.toArray(new Creature[list.size()]);
	}
	
	@Override
	public Creature getFinalTarget(Creature caster, Creature target, L2Skill skill)
	{
		if (caster.getActingPlayer() == null)
			return null;
		
		return caster;
	}
}