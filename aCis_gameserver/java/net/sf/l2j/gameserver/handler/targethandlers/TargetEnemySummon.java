package net.sf.l2j.gameserver.handler.targethandlers;

import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetEnemySummon implements ITargetHandler
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
		return SkillTargetType.ENEMY_SUMMON;
	}
	
	@Override
	public Creature getFinalTarget(Creature target, Creature caster, L2Skill skill, boolean isCtrlPressed)
	{
		// TODO Check if Betray/Erase can be cast on Servitor (not Summon) as well as Player
		if (!(caster instanceof Player && target instanceof Summon))
			return null;
		
		final Player summonOwner = target.getActingPlayer();
		if (!(caster.getSummon() != target && !target.isDead() && (summonOwner.getPvpFlag() != 0 || summonOwner.getKarma() > 0) || (summonOwner.isInsideZone(ZoneId.PVP) && caster.isInsideZone(ZoneId.PVP)) || (summonOwner.isInDuel() && ((Player) caster).isInDuel() && summonOwner.getDuelId() == ((Player) caster).getDuelId())))
			return null;
		
		return target;
	}
}