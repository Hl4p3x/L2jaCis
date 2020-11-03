package net.sf.l2j.gameserver.model.actor.ai.type;

import net.sf.l2j.commons.concurrent.ThreadPool;

import net.sf.l2j.gameserver.data.manager.CursedWeaponManager;
import net.sf.l2j.gameserver.enums.AiEventType;
import net.sf.l2j.gameserver.enums.IntentionType;
import net.sf.l2j.gameserver.enums.LootRule;
import net.sf.l2j.gameserver.enums.items.ArmorType;
import net.sf.l2j.gameserver.enums.items.EtcItemType;
import net.sf.l2j.gameserver.enums.items.WeaponType;
import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.enums.skills.SkillType;
import net.sf.l2j.gameserver.handler.IItemHandler;
import net.sf.l2j.gameserver.handler.ItemHandler;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Boat;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.actor.instance.StaticObject;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.location.BoatEntrance;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.AutoAttackStart;
import net.sf.l2j.gameserver.network.serverpackets.ChairSit;
import net.sf.l2j.gameserver.network.serverpackets.MoveToLocationInVehicle;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.skills.L2Skill;
import net.sf.l2j.gameserver.taskmanager.AttackStanceTaskManager;
import net.sf.l2j.gameserver.taskmanager.ItemsOnGroundTaskManager;

public class PlayerAI extends PlayableAI
{
	public PlayerAI(Player player)
	{
		super(player);
	}
	
	@Override
	protected void onEvtArrived()
	{
		if (_currentIntention.getType() == IntentionType.MOVE_TO && _currentIntention.getSecondParameter() != null)
		{
			final Boat boat = (Boat) _currentIntention.getSecondParameter();
			final BoatEntrance closestEntrance = boat.getClosestEntrance(getActor().getPosition());
			
			getActor().getBoatPosition().set(closestEntrance.getInnerLocation());
			
			// Since we're close enough to the boat we just send client onboarding packet without any movement on the server.
			getActor().broadcastPacket(new MoveToLocationInVehicle(getActor(), boat, closestEntrance.getInnerLocation(), getActor().getPosition()));
		}
		
		super.onEvtArrived();
	}
	
	@Override
	protected void onEvtSatDown(WorldObject target)
	{
		if (_nextIntention.isBlank())
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
		else
			changeCurrentIntention(_nextIntention);
	}
	
	@Override
	protected void onEvtStoodUp()
	{
		if (getActor().getThroneId() != 0)
		{
			final WorldObject object = World.getInstance().getObject(getActor().getThroneId());
			if (object instanceof StaticObject)
				((StaticObject) object).setBusy(false);
			
			getActor().setThroneId(0);
		}
		
		if (_nextIntention.isBlank())
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
		else
			changeCurrentIntention(_nextIntention);
	}
	
	@Override
	protected void onEvtBowAttackReused()
	{
		if (getActor().getAttackType() == WeaponType.BOW)
		{
			// Attacks can be scheduled while isAttackingNow
			if (_nextIntention.getType() == IntentionType.ATTACK)
			{
				changeCurrentIntention(_nextIntention);
				return;
			}
			
			if (_currentIntention.getType() == IntentionType.ATTACK)
			{
				final Creature target = (Creature) _currentIntention.getFirstParameter();
				if (target.canAttackingContinueBy(getActor()))
					notifyEvent(AiEventType.THINK, null, null);
				else
					changeCurrentIntention(IntentionType.ACTIVE, null, null);
			}
		}
	}
	
	@Override
	protected void onEvtAttacked(Creature attacker)
	{
		if (getActor().getTamedBeast() != null)
			getActor().getTamedBeast().getAI().notifyEvent(AiEventType.OWNER_ATTACKED, attacker, null);
		
		if (getActor().isSitting())
			changeCurrentIntention(IntentionType.STAND, null, null);
		
		super.onEvtAttacked(attacker);
	}
	
	@Override
	protected void onEvtCancel()
	{
		getActor().getCast().stop();
		getActor().getMove().cancelFollowTask();
		changeCurrentIntention(IntentionType.ACTIVE, null, null);
	}
	
	@Override
	protected void onIntentionSit(WorldObject target)
	{
		setCurrentIntention(IntentionType.SIT, target, null);
		notifyEvent(AiEventType.THINK, null, null);
	}
	
	@Override
	protected void onIntentionStand()
	{
		setCurrentIntention(IntentionType.STAND, null, null);
		notifyEvent(AiEventType.THINK, null, null);
	}
	
	@Override
	protected void onIntentionActive()
	{
		setCurrentIntention(IntentionType.IDLE, null, null);
	}
	
	@Override
	protected void onIntentionInteract(WorldObject object, boolean isShiftPressed)
	{
		setCurrentIntention(IntentionType.INTERACT, object, isShiftPressed);
		notifyEvent(AiEventType.THINK, null, null);
	}
	
	@Override
	protected void onIntentionUseItem(Integer objectId)
	{
		setCurrentIntention(IntentionType.USE_ITEM, objectId, null);
		notifyEvent(AiEventType.THINK, null, null);
	}
	
	@Override
	protected void onIntentionFakeDeath(boolean startFakeDeath)
	{
		if (getActor().denyAiAction() || getActor().getMountType() != 0)
		{
			clientActionFailed();
			return;
		}
		
		if (startFakeDeath)
		{
			getActor().getMove().stop();
			getActor().startFakeDeath();
		}
		else
			getActor().stopFakeDeath(false);
	}
	
	@Override
	protected void thinkCast()
	{
		if (getActor().denyAiAction() || getActor().getAllSkillsDisabled() || getActor().getCast().isCastingNow())
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			clientActionFailed();
			return;
		}
		
		final SkillUseHolder skillUseHolder = (SkillUseHolder) _currentIntention.getFirstParameter();
		final Creature target = skillUseHolder.getFinalTarget();
		if (target == null)
		{
			getActor().sendPacket(SystemMessageId.INVALID_TARGET);
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			return;
		}
		if (targetLost(target))
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			return;
		}
		
		if (!getActor().getCast().canAttemptCast(skillUseHolder))
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			return;
		}
		
		final L2Skill skill = skillUseHolder.getSkill();
		final boolean isShiftPressed = skillUseHolder.isShiftPressed();
		if (skill.getTargetType() == SkillTargetType.GROUND)
		{
			if (getActor().getMove().maybeMoveToPosition(getActor().getCurrentSkillWorldPosition(), skill.getCastRange(), isShiftPressed))
			{
				if (isShiftPressed)
				{
					getActor().sendPacket(SystemMessageId.TARGET_TOO_FAR);
					changeCurrentIntention(IntentionType.ACTIVE, null, null);
				}
				
				return;
			}
		}
		else
		{
			if (_actor.getMove().maybeMoveToPawn(target, skill.getCastRange(), isShiftPressed))
			{
				if (isShiftPressed)
				{
					getActor().sendPacket(SystemMessageId.TARGET_TOO_FAR);
					changeCurrentIntention(IntentionType.ACTIVE, null, null);
				}
				
				return;
			}
		}
		
		if (skill.isToggle())
		{
			getActor().getMove().stop();
			getActor().getCast().doToggleCast(skillUseHolder);
		}
		else
		{
			if (!getActor().getCast().canDoCast(skillUseHolder))
			{
				if (skill.nextActionIsAttack() && skillUseHolder.getFinalTarget() instanceof Attackable)
					changeCurrentIntention(IntentionType.ATTACK, skillUseHolder.getFinalTarget(), skillUseHolder.isShiftPressed());
				else
					changeCurrentIntention(IntentionType.ACTIVE, null, null);
				
				return;
			}
			
			if (skill.getHitTime() > 50)
				getActor().getMove().stop();
			
			if (skill.getSkillType() == SkillType.FUSION || skill.getSkillType() == SkillType.SIGNET_CASTTIME)
				getActor().getCast().doFusionCasttimeCast(skillUseHolder);
			else
			{
				final ItemInstance itemInstance = (ItemInstance) _currentIntention.getSecondParameter();
				getActor().getCast().doCast(skillUseHolder, itemInstance);
			}
		}
	}
	
	@Override
	protected ItemInstance thinkPickUp()
	{
		final ItemInstance item = super.thinkPickUp();
		
		if (item == null)
			return null;
		
		synchronized (item)
		{
			if (!item.isVisible())
				return null;
			
			if (!getActor().getInventory().validateWeight(item.getCount() * item.getItem().getWeight()))
			{
				getActor().sendPacket(SystemMessageId.WEIGHT_LIMIT_EXCEEDED);
				clientActionFailed();
				return null;
			}
			
			if (((getActor().isInParty() && getActor().getParty().getLootRule() == LootRule.ITEM_LOOTER) || !getActor().isInParty()) && !getActor().getInventory().validateCapacity(item))
			{
				getActor().sendPacket(SystemMessageId.SLOTS_FULL);
				clientActionFailed();
				return null;
			}
			
			if (getActor().getActiveTradeList() != null)
			{
				getActor().sendPacket(SystemMessageId.CANNOT_PICKUP_OR_USE_ITEM_WHILE_TRADING);
				clientActionFailed();
				return null;
			}
			
			if (item.getOwnerId() != 0 && !getActor().isLooterOrInLooterParty(item.getOwnerId()))
			{
				if (item.getItemId() == 57)
					getActor().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.FAILED_TO_PICKUP_S1_ADENA).addNumber(item.getCount()));
				else if (item.getCount() > 1)
					getActor().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.FAILED_TO_PICKUP_S2_S1_S).addItemName(item).addNumber(item.getCount()));
				else
					getActor().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.FAILED_TO_PICKUP_S1).addItemName(item));
				
				clientActionFailed();
				return null;
			}
			
			if (item.hasDropProtection())
				item.removeDropProtection();
			
			item.pickupMe(getActor());
			
			ItemsOnGroundTaskManager.getInstance().remove(item);
		}
		
		if (item.getItemType() == EtcItemType.HERB)
		{
			final IItemHandler handler = ItemHandler.getInstance().getHandler(item.getEtcItem());
			if (handler != null)
				handler.useItem(getActor(), item, false);
			
			item.destroyMe("Consume", getActor(), null);
		}
		else if (CursedWeaponManager.getInstance().isCursed(item.getItemId()))
		{
			getActor().addItem("Pickup", item, null, true);
		}
		else
		{
			if (item.getItemType() instanceof ArmorType || item.getItemType() instanceof WeaponType)
			{
				SystemMessage msg;
				if (item.getEnchantLevel() > 0)
					msg = SystemMessage.getSystemMessage(SystemMessageId.ATTENTION_S1_PICKED_UP_S2_S3).addString(getActor().getName()).addNumber(item.getEnchantLevel()).addItemName(item.getItemId());
				else
					msg = SystemMessage.getSystemMessage(SystemMessageId.ATTENTION_S1_PICKED_UP_S2).addString(getActor().getName()).addItemName(item.getItemId());
				
				getActor().broadcastPacketInRadius(msg, 1400);
			}
			
			if (getActor().isInParty())
				getActor().getParty().distributeItem(getActor(), item);
			else if (item.getItemId() == 57 && getActor().getInventory().getAdenaInstance() != null)
			{
				getActor().addAdena("Pickup", item.getCount(), null, true);
				item.destroyMe("Pickup", getActor(), null);
			}
			else
				getActor().addItem("Pickup", item, null, true);
		}
		
		ThreadPool.schedule(() -> getActor().setIsParalyzed(false), (int) (700 / getActor().getStat().getMovementSpeedMultiplier()));
		getActor().setIsParalyzed(true);
		
		return item;
	}
	
	@Override
	protected void thinkInteract()
	{
		if (getActor().denyAiAction() || getActor().isSitting() || getActor().isFlying())
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			clientActionFailed();
			return;
		}
		
		final WorldObject target = (WorldObject) _currentIntention.getFirstParameter();
		if (target == null || targetLost(target))
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			return;
		}
		
		if (!getActor().getAI().canAttemptInteract())
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			return;
		}
		
		final boolean isShiftPressed = (boolean) _currentIntention.getSecondParameter();
		if (getActor().getMove().maybeMoveToPawn(target, 60, isShiftPressed))
		{
			if (isShiftPressed)
			{
				changeCurrentIntention(IntentionType.ACTIVE, null, null);
				clientActionFailed();
			}
			
			return;
		}
		
		if (!getActor().getAI().canDoInteract(target))
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			return;
		}
		
		clientActionFailed();
		getActor().getMove().stop();
		((Creature) target).onInteract(getActor());
		
		changeCurrentIntention(IntentionType.ACTIVE, null, null);
	}
	
	@Override
	protected void thinkSit()
	{
		if (getActor().denyAiAction() || getActor().isSitting() || getActor().isOperating() || getActor().getMountType() != 0)
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			clientActionFailed();
			return;
		}
		
		getActor().getMove().stop();
		
		// sitDown sends the ChangeWaitType packet, which MUST precede the ChairSit packet (sent in this function) in order to properly sit on the throne.
		getActor().sitDown();
		
		final WorldObject target = (WorldObject) _currentIntention.getFirstParameter();
		final boolean isThrone = target instanceof StaticObject && ((StaticObject) target).getType() == 1;
		if (isThrone && !((StaticObject) target).isBusy() && getActor().isIn3DRadius(target, Npc.INTERACTION_DISTANCE))
		{
			getActor().setThroneId(target.getObjectId());
			
			((StaticObject) target).setBusy(true);
			getActor().broadcastPacket(new ChairSit(getActor().getObjectId(), ((StaticObject) target).getStaticObjectId()));
		}
	}
	
	@Override
	protected void thinkStand()
	{
		// no need to getActor().isOperating() here, because it is included in the Player overriden denyAiAction
		if (getActor().denyAiAction() || !getActor().isSitting() || getActor().getMountType() != 0)
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			clientActionFailed();
			return;
		}
		
		if (getActor().isFakeDeath())
			getActor().stopFakeDeath(true);
		else
			getActor().standUp();
	}
	
	@Override
	protected void thinkUseItem()
	{
		final Integer objectId = (Integer) _currentIntention.getFirstParameter();
		final ItemInstance itemToTest = getActor().getInventory().getItemByObjectId(objectId);
		if (itemToTest == null)
			return;
		
		getActor().useEquippableItem(itemToTest, false);
		
		// Simulate onEvtEndUseItem
		if (_previousIntention.getType() == IntentionType.ATTACK)
		{
			final Creature target = (Creature) _previousIntention.getFirstParameter();
			if (target.canAttackingContinueBy(getActor()))
				changeCurrentIntention(_previousIntention);
		}
		else
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
	}
	
	/**
	 * Manage Interact Task with another Player.<BR>
	 * Turn the character in front of the target.<BR>
	 * In case of private stores, send the related packet.
	 * @param target The Creature targeted
	 */
	public void doInteract(Creature target)
	{
		target.onInteract(getActor());
	}
	
	@Override
	public boolean canAttemptInteract()
	{
		if (getActor().isOperating() || getActor().isProcessingTransaction())
			return false;
		
		return true;
	}
	
	@Override
	public boolean canDoInteract(WorldObject target)
	{
		// Can't interact with StaticObjects (Signs etc)
		if (target instanceof StaticObject)
			return false;
		
		// Can't interact while casting a spell.
		if (getActor().getCast().isCastingNow())
			return false;
		
		// Can't interact while dead.
		if (getActor().isDead() || getActor().isFakeDeath())
			return false;
		
		// Can't interact sitted.
		if (getActor().isSitting())
			return false;
		
		// Can't interact in shop mode, or during a transaction or a request.
		if (getActor().isOperating() || getActor().isProcessingTransaction())
			return false;
		
		// Can't interact if regular distance doesn't match.
		if (!target.isIn3DRadius(getActor(), Npc.INTERACTION_DISTANCE))
			return false;
		
		return true;
	}
	
	@Override
	public void startAttackStance()
	{
		if (!AttackStanceTaskManager.getInstance().isInAttackStance(getActor()))
		{
			final Summon summon = getActor().getSummon();
			if (summon != null)
				summon.broadcastPacket(new AutoAttackStart(summon.getObjectId()));
			
			getActor().broadcastPacket(new AutoAttackStart(getActor().getObjectId()));
		}
		
		AttackStanceTaskManager.getInstance().add(getActor());
	}
	
	@Override
	public void clientActionFailed()
	{
		getActor().sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public Player getActor()
	{
		return (Player) _actor;
	}
	
}