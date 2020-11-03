package net.sf.l2j.gameserver.model.actor.cast;

import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.Folk;
import net.sf.l2j.gameserver.model.actor.instance.Guard;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.skills.L2Skill;

/**
 * This class groups all cast data related to a {@link Player}.
 * @param <T> : The {@link Playable} used as actor.
 */
public class PlayableCast<T extends Playable> extends CreatureCast<T>
{
	public PlayableCast(T actor)
	{
		super(actor);
	}
	
	@Override
	public void doInstantCast(L2Skill skill, ItemInstance item)
	{
		if (!item.isHerb() && !_actor.destroyItem("Consume", item.getObjectId(), (skill.getItemConsumeId() == 0 && skill.getItemConsume() > 0) ? skill.getItemConsume() : 1, null, false))
		{
			_actor.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
			return;
		}
		
		int reuseDelay = skill.getReuseDelay();
		if (reuseDelay > 10)
			_actor.disableSkill(skill, reuseDelay);
		
		_actor.broadcastPacket(new MagicSkillUse(_actor, _actor, skill.getId(), skill.getLevel(), 0, 0));
		
		callSkill(skill, new Creature[]
		{
			_actor
		});
	}
	
	@Override
	public void doCast(L2Skill skill, Creature target, ItemInstance itemInstance)
	{
		if (itemInstance != null)
		{
			// Consume item if needed.
			if (!(itemInstance.isHerb() || itemInstance.isSummonItem()) && !_actor.destroyItem("Consume", itemInstance.getObjectId(), 1, null, false))
			{
				_actor.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
				return;
			}
			
			// Set item timestamp.
			_actor.addItemSkillTimeStamp(skill, itemInstance);
		}
		
		super.doCast(skill, target, null);
	}
	
	@Override
	public boolean canDoCast(Creature target, L2Skill skill, boolean isCtrlPressed, int itemObjectId)
	{
		if (!super.canDoCast(target, skill, isCtrlPressed, itemObjectId))
			return false;
		
		// Check item consumption validity.
		if (itemObjectId != 0 && _actor.getInventory().getItemByObjectId(itemObjectId) == null)
		{
			_actor.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
			return false;
		}
		
		if (skill.isOffensive())
		{
			if (target instanceof Playable)
			{
				final Playable playableTarget = (Playable) target;
				
				// Skills that have the playable as a target (Self, Aura, Area-Summon etc) are exempt from PvP checks
				if (playableTarget.getActingPlayer() != _actor.getActingPlayer() && !_actor.getActingPlayer().canCastOffensiveSkillOnPlayable(playableTarget, skill, isCtrlPressed))
				{
					_actor.sendPacket(SystemMessageId.INVALID_TARGET);
					return false;
				}
				
				// Skills that have the playable as a target (Self, Aura, Area-Summon etc) can be used before olympiad starts
				if (playableTarget.getActingPlayer() != _actor.getActingPlayer() && _actor.getActingPlayer().isInOlympiadMode() && !_actor.getActingPlayer().isOlympiadStart())
				{
					_actor.sendPacket(SystemMessageId.INVALID_TARGET);
					return false;
				}
				
				if (_actor.isInsideZone(ZoneId.PEACE) && !_actor.getActingPlayer().getAccessLevel().allowPeaceAttack())
				{
					_actor.sendPacket(SystemMessageId.CANT_ATK_PEACEZONE);
					return false;
				}
			}
			// You can damage Folk and Guard with CTRL, but nothing else.
			else if (target instanceof Folk || target instanceof Guard)
			{
				if (!skill.isDamage() || !isCtrlPressed)
				{
					_actor.sendPacket(SystemMessageId.INVALID_TARGET);
					return false;
				}
			}
		}
		else
		{
			if (target instanceof Playable)
			{
				if (!_actor.getActingPlayer().canCastBeneficialSkillOnPlayable((Playable) target, skill, isCtrlPressed))
				{
					_actor.sendPacket(SystemMessageId.INVALID_TARGET);
					return false;
				}
			}
			else if (target instanceof Monster && !isCtrlPressed)
			{
				_actor.sendPacket(SystemMessageId.INVALID_TARGET);
				return false;
			}
		}
		
		return true;
	}
}