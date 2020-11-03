package net.sf.l2j.gameserver.handler.targethandlers;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.Pet;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.skills.L2Skill;

public class TargetCorpsePet implements ITargetHandler
{
	@Override
	public Creature[] getTargetList(SkillUseHolder skillUseHolder)
	{
		final Creature caster = skillUseHolder.getCaster();
		final Creature target = caster.getSummon();
		if (target != null && target.isDead())
		{
			final Pet targetPet;
			if (target instanceof Pet)
				targetPet = (Pet) target;
			else
				targetPet = null;
			
			boolean condGood = true;
			final Player player = (Player) caster;
			if (targetPet != null)
			{
				if (targetPet.getOwner() != player)
				{
					if (targetPet.getOwner().isReviveRequested())
					{
						if (targetPet.getOwner().isRevivingPet())
							player.sendPacket(SystemMessageId.RES_HAS_ALREADY_BEEN_PROPOSED);
						else
							player.sendPacket(SystemMessageId.CANNOT_RES_PET2);
						
						condGood = false;
					}
				}
			}
			
			if (condGood)
				return new Creature[]
				{
					target
				};
			
		}
		
		return EMPTY_TARGET_ARRAY;
	}
	
	@Override
	public SkillTargetType getTargetType()
	{
		return SkillTargetType.CORPSE_PET;
	}
	
	@Override
	public Creature getFinalTarget(Creature target, Creature caster, L2Skill skill, boolean isCtrlPressed)
	{
		if (!(caster instanceof Player))
			return null;
		
		final Creature summon = caster.getSummon();
		if (summon == null || !summon.isDead())
			return null;
		
		return summon;
	}
}