package net.sf.l2j.gameserver.model.actor.ai.type;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.data.manager.CursedWeaponManager;
import net.sf.l2j.gameserver.enums.IntentionType;
import net.sf.l2j.gameserver.enums.LootRule;
import net.sf.l2j.gameserver.enums.items.ArmorType;
import net.sf.l2j.gameserver.enums.items.EtcItemType;
import net.sf.l2j.gameserver.enums.items.WeaponType;
import net.sf.l2j.gameserver.handler.IItemHandler;
import net.sf.l2j.gameserver.handler.ItemHandler;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.group.Party;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.AutoAttackStart;
import net.sf.l2j.gameserver.network.serverpackets.AutoAttackStop;
import net.sf.l2j.gameserver.network.serverpackets.PetItemList;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.taskmanager.AttackStanceTaskManager;
import net.sf.l2j.gameserver.taskmanager.ItemsOnGroundTaskManager;

public class SummonAI extends PlayableAI
{
	private static final int AVOID_RADIUS = 70;
	private volatile boolean _followOwner = true;
	
	public SummonAI(Summon summon)
	{
		super(summon);
	}
	
	@Override
	protected void onIntentionIdle()
	{
		super.onIntentionIdle();
		_followOwner = false;
	}
	
	@Override
	protected void onIntentionActive()
	{
		if (_nextIntention.isBlank())
		{
			if (_followOwner)
				changeCurrentIntention(IntentionType.FOLLOW, getOwner(), false);
			else
				onIntentionIdle();
		}
		else
			super.onIntentionActive();
	}
	
	@Override
	protected void onEvtTeleported()
	{
		_followOwner = true;
		changeCurrentIntention(IntentionType.FOLLOW, getOwner(), false);
	}
	
	@Override
	protected void onEvtFinishedCasting()
	{
		if (_nextIntention.isBlank())
		{
			if (_previousIntention.getType() == IntentionType.ATTACK)
				changeCurrentIntention(_previousIntention);
			else
				changeCurrentIntention(IntentionType.ACTIVE, null, null);
		}
		else
			changeCurrentIntention(_nextIntention);
	}
	
	@Override
	public void onEvtAttacked(Creature attacker)
	{
		super.onEvtAttacked(attacker);
		
		avoidAttack(attacker);
	}
	
	@Override
	protected void onEvtEvaded(Creature attacker)
	{
		super.onEvtEvaded(attacker);
		
		avoidAttack(attacker);
	}
	
	@Override
	protected ItemInstance thinkPickUp()
	{
		final ItemInstance item = super.thinkPickUp();
		
		if (item == null)
			return null;
		
		if (CursedWeaponManager.getInstance().isCursed(item.getItemId()))
		{
			getOwner().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.FAILED_TO_PICKUP_S1).addItemName(item.getItemId()));
			return null;
		}
		
		if (item.getItem().getItemType() == EtcItemType.ARROW || item.getItem().getItemType() == EtcItemType.SHOT)
		{
			getOwner().sendPacket(SystemMessageId.ITEM_NOT_FOR_PETS);
			return null;
		}
		
		synchronized (item)
		{
			if (!item.isVisible())
				return null;
			
			if (!getActor().getInventory().validateCapacity(item))
			{
				getOwner().sendPacket(SystemMessageId.YOUR_PET_CANNOT_CARRY_ANY_MORE_ITEMS);
				return null;
			}
			
			if (!getActor().getInventory().validateWeight(item, item.getCount()))
			{
				getOwner().sendPacket(SystemMessageId.UNABLE_TO_PLACE_ITEM_YOUR_PET_IS_TOO_ENCUMBERED);
				return null;
			}
			
			if (item.getOwnerId() != 0 && !getActor().getOwner().isLooterOrInLooterParty(item.getOwnerId()))
			{
				if (item.getItemId() == 57)
					getOwner().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.FAILED_TO_PICKUP_S1_ADENA).addNumber(item.getCount()));
				else if (item.getCount() > 1)
					getOwner().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.FAILED_TO_PICKUP_S2_S1_S).addItemName(item.getItemId()).addNumber(item.getCount()));
				else
					getOwner().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.FAILED_TO_PICKUP_S1).addItemName(item.getItemId()));
				
				return null;
			}
			
			if (item.hasDropProtection())
				item.removeDropProtection();
			
			final Party party = getActor().getOwner().getParty();
			if (party != null && party.getLootRule() != LootRule.ITEM_LOOTER)
				party.distributeItem(getActor().getOwner(), item);
			else
				item.pickupMe(_actor);
			
			ItemsOnGroundTaskManager.getInstance().remove(item);
		}
		
		if (item.getItemType() == EtcItemType.HERB)
		{
			final IItemHandler handler = ItemHandler.getInstance().getHandler(item.getEtcItem());
			if (handler != null)
				handler.useItem(getActor(), item, false);
			
			item.destroyMe("Consume", getActor().getOwner(), null);
			getActor().broadcastStatusUpdate();
		}
		else
		{
			if (item.getItemType() instanceof ArmorType || item.getItemType() instanceof WeaponType)
			{
				SystemMessage msg;
				if (item.getEnchantLevel() > 0)
					msg = SystemMessage.getSystemMessage(SystemMessageId.ATTENTION_S1_PET_PICKED_UP_S2_S3).addCharName(getActor().getOwner()).addNumber(item.getEnchantLevel()).addItemName(item.getItemId());
				else
					msg = SystemMessage.getSystemMessage(SystemMessageId.ATTENTION_S1_PET_PICKED_UP_S2).addCharName(getActor().getOwner()).addItemName(item.getItemId());
				
				getOwner().broadcastPacketInRadius(msg, 1400);
			}
			
			SystemMessage sm2;
			if (item.getItemId() == 57)
				sm2 = SystemMessage.getSystemMessage(SystemMessageId.PET_PICKED_S1_ADENA).addItemNumber(item.getCount());
			else if (item.getEnchantLevel() > 0)
				sm2 = SystemMessage.getSystemMessage(SystemMessageId.PET_PICKED_S1_S2).addNumber(item.getEnchantLevel()).addItemName(item.getItemId());
			else if (item.getCount() > 1)
				sm2 = SystemMessage.getSystemMessage(SystemMessageId.PET_PICKED_S2_S1_S).addItemName(item.getItemId()).addItemNumber(item.getCount());
			else
				sm2 = SystemMessage.getSystemMessage(SystemMessageId.PET_PICKED_S1).addItemName(item.getItemId());
			
			getOwner().sendPacket(sm2);
			getActor().getInventory().addItem("Pickup", item, getOwner(), getActor());
			getOwner().sendPacket(new PetItemList(getActor()));
		}
		
		if (_followOwner)
			getActor().followOwner();
		
		return item;
	}
	
	@Override
	public Summon getActor()
	{
		return (Summon) _actor;
	}
	
	private Player getOwner()
	{
		return getActor().getOwner();
	}
	
	@Override
	public void startAttackStance()
	{
		if (!AttackStanceTaskManager.getInstance().isInAttackStance(getOwner()))
		{
			getActor().broadcastPacket(new AutoAttackStart(getActor().getObjectId()));
			getOwner().broadcastPacket(new AutoAttackStart(getOwner().getObjectId()));
		}
		
		AttackStanceTaskManager.getInstance().add(getOwner());
	}
	
	@Override
	public void stopAttackStance()
	{
		getActor().broadcastPacket(new AutoAttackStop(getActor().getObjectId()));
	}
	
	private void avoidAttack(Creature attacker)
	{
		final Player owner = getOwner();
		
		if (owner == null || owner == attacker || !owner.isIn3DRadius(_actor, 2 * AVOID_RADIUS) || !AttackStanceTaskManager.getInstance().isInAttackStance(owner))
			return;
		
		if (_currentIntention.getType() != IntentionType.ACTIVE && _currentIntention.getType() != IntentionType.FOLLOW)
			return;
		
		if (_actor.isMoving() || _actor.isDead() || _actor.isMovementDisabled())
			return;
		
		final int ownerX = owner.getX();
		final int ownerY = owner.getY();
		final double angle = Math.toRadians(Rnd.get(-90, 90)) + Math.atan2(ownerY - _actor.getY(), ownerX - _actor.getX());
		
		final int targetX = ownerX + (int) (AVOID_RADIUS * Math.cos(angle));
		final int targetY = ownerY + (int) (AVOID_RADIUS * Math.sin(angle));
		
		_actor.getMove().moveToLocation(targetX, targetY, _actor.getZ());
	}
	
	public void switchFollowStatus()
	{
		setFollowStatus(!_followOwner);
	}
	
	@Override
	public void setFollowStatus(boolean state)
	{
		_followOwner = state;
		
		if (_followOwner)
			tryTo(IntentionType.FOLLOW, getOwner(), false);
		else
			tryTo(IntentionType.IDLE, null, null);
	}
	
	@Override
	public boolean getFollowStatus()
	{
		return _followOwner;
	}
}