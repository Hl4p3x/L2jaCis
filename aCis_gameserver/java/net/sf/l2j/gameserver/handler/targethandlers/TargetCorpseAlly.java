package net.sf.l2j.gameserver.handler.targethandlers;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetCorpseAlly implements ITargetHandler
{
	@Override
	public Creature[] getTargetList(SkillUseHolder skillUseHolder)
	{
		final L2Skill skill = skillUseHolder.getSkill();
		final Creature caster = skillUseHolder.getCaster();
		final Player player = caster.getActingPlayer();
		if (player.isInOlympiadMode())
			return new Creature[]
			{
				caster
			};
		
		final List<Creature> list = new ArrayList<>();
		list.add(caster);
		
		if (player.getClan() != null)
		{
			final boolean isInBossZone = player.isInsideZone(ZoneId.BOSS);
			
			for (Player obj : caster.getKnownTypeInRadius(Player.class, skill.getSkillRadius()))
			{
				if (!obj.isDead())
					continue;
				
				if ((obj.getAllyId() == 0 || obj.getAllyId() != player.getAllyId()) && (obj.getClan() == null || obj.getClanId() != player.getClanId()))
					continue;
				
				if (player.isInDuel())
				{
					if (player.getDuelId() != obj.getDuelId())
						continue;
					
					if (player.isInParty() && obj.isInParty() && player.getParty().getLeaderObjectId() != obj.getParty().getLeaderObjectId())
						continue;
				}
				
				// Siege battlefield resurrect has been made possible for participants
				if (obj.isInsideZone(ZoneId.SIEGE) && !obj.isInSiege())
					continue;
				
				// Check if both caster and target are in a boss zone.
				if (isInBossZone != obj.isInsideZone(ZoneId.BOSS))
					continue;
				
				list.add(obj);
			}
		}
		
		return list.toArray(new Creature[list.size()]);
	}
	
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.CORPSE_ALLY;
	}
	
	@Override
	public Creature getFinalTarget(Creature target, Creature caster, L2Skill skill, boolean isCtrlPressed)
	{
		final Player player = caster.getActingPlayer();
		if (player == null)
			return null;
		
		return caster;
	}
}