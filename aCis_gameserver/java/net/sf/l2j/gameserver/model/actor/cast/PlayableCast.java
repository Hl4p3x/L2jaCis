package net.sf.l2j.gameserver.model.actor.cast;

import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.Folk;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.skills.L2Skill;

/**
 * This class groups all cast data related to a {@link Player}.
 */
public class PlayableCast extends CreatureCast
{
	public PlayableCast(Creature creature)
	{
		super(creature);
	}
	
	@Override
	public void doInstantCast(L2Skill skill, ItemInstance item)
	{
		if (!item.isHerb() && !_creature.destroyItem("Consume", item.getObjectId(), (skill.getItemConsumeId() == 0 && skill.getItemConsume() > 0) ? skill.getItemConsume() : 1, null, false))
		{
			_creature.getActingPlayer().sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
			return;
		}
		
		int reuseDelay = skill.getReuseDelay();
		if (reuseDelay > 10)
			_creature.disableSkill(skill, reuseDelay);
		
		_creature.broadcastPacket(new MagicSkillUse(_creature, _creature, skill.getId(), skill.getLevel(), 0, 0));
		
		if (_creature instanceof Player)
		{
			_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.USE_S1).addSkillName(skill));
			
			if (skill.getNumCharges() > 0)
			{
				if (skill.getMaxCharges() > 0)
					((Player) _creature).increaseCharges(skill.getNumCharges(), skill.getMaxCharges());
				else
					((Player) _creature).decreaseCharges(skill.getNumCharges());
			}
		}
		
		callSkill(skill, new Creature[]
		{
			_creature
		});
		
		_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT).addSkillName(skill));
	}
	
	@Override
	public void doCast(SkillUseHolder skillUseHolder, ItemInstance itemInstance)
	{
		if (itemInstance != null)
			((Playable) _creature).addItemSkillTimeStamp(skillUseHolder.getSkill(), itemInstance);
		
		super.doCast(skillUseHolder, null);
	}
	
	@Override
	public boolean canDoCast(SkillUseHolder skillUseHolder)
	{
		if (!super.canDoCast(skillUseHolder))
			return false;
		
		final Playable playable = (Playable) _creature;
		final L2Skill skill = skillUseHolder.getSkill();
		final ItemInstance itemInstance = (ItemInstance) playable.getAI().getCurrentIntention().getSecondParameter();
		if (itemInstance != null && !playable.destroyItem("Consume", itemInstance.getObjectId(), (skill.getItemConsumeId() == 0 && skill.getItemConsume() > 0) ? skill.getItemConsume() : 1, null, false))
		{
			// Event possible when scheduling an ItemSkills type of skill and you've destroyed the items in the meantime
			playable.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
			return false;
		}
		
		final Creature target = skillUseHolder.getFinalTarget();
		final boolean isCtrlPressed = skillUseHolder.isCtrlPressed();
		if (skill.isOffensive())
		{
			if (target instanceof Playable)
			{
				final Playable playableTarget = (Playable) target;
				// Skills that have the playable as a target (Self, Aura, Area-Summon etc) are exempt from PvP checks
				if (playableTarget.getActingPlayer() != playable.getActingPlayer() && !playable.getActingPlayer().canCastOffensiveSkillOnPlayable(playableTarget, skill, isCtrlPressed))
				{
					playable.sendPacket(SystemMessageId.INVALID_TARGET);
					return false;
				}
				
				// Skills that have the playable as a target (Self, Aura, Area-Summon etc) can be used before olympiad starts
				if (playableTarget.getActingPlayer() != playable.getActingPlayer() && playable.getActingPlayer().isInOlympiadMode() && !playable.getActingPlayer().isOlympiadStart())
				{
					playable.sendPacket(SystemMessageId.INVALID_TARGET);
					return false;
				}
				
				if (playable.isInsideZone(ZoneId.PEACE) && !playable.getActingPlayer().getAccessLevel().allowPeaceAttack())
				{
					playable.sendPacket(SystemMessageId.CANT_ATK_PEACEZONE);
					return false;
				}
			}
			// Folk do not care if they are in a PEACE zone or not, behaviour is the same
			else if (target instanceof Folk)
			{
				// You can damage Folk-type with CTRL
				if (skill.isDamage())
				{
					if (!isCtrlPressed)
					{
						playable.sendPacket(SystemMessageId.INVALID_TARGET);
						return false;
					}
				}
				// but nothing else
				else
				{
					playable.sendPacket(SystemMessageId.INVALID_TARGET);
					return false;
				}
			}
		}
		else
		{
			if (target instanceof Playable)
			{
				if (!playable.getActingPlayer().canCastBeneficialSkillOnPlayable((Playable) target, skill, isCtrlPressed))
				{
					playable.sendPacket(SystemMessageId.INVALID_TARGET);
					return false;
				}
			}
			else if (target instanceof Monster && !isCtrlPressed)
			{
				playable.sendPacket(SystemMessageId.INVALID_TARGET);
				return false;
			}
		}
		
		return true;
	}
}