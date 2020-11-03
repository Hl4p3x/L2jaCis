package net.sf.l2j.gameserver.model.actor.ai.type;

import net.sf.l2j.gameserver.enums.AiEventType;
import net.sf.l2j.gameserver.enums.IntentionType;
import net.sf.l2j.gameserver.enums.items.WeaponType;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Boat;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.location.SpawnLocation;
import net.sf.l2j.gameserver.network.serverpackets.Die;
import net.sf.l2j.gameserver.skills.L2Skill;

public class CreatureAI extends AbstractAI
{
	public CreatureAI(Creature actor)
	{
		super(actor);
	}
	
	@Override
	protected void onIntentionIdle()
	{
		setCurrentIntention(IntentionType.IDLE, null, null);
		getActor().getMove().stop();
	}
	
	@Override
	protected void onIntentionActive()
	{
		setCurrentIntention(IntentionType.ACTIVE, null, null);
	}
	
	@Override
	protected void onIntentionAttack(Creature target, boolean isShiftPressed)
	{
		setCurrentIntention(IntentionType.ATTACK, target, isShiftPressed);
		notifyEvent(AiEventType.THINK, null, null);
	}
	
	@Override
	protected void onIntentionCast(SkillUseHolder skillUseHolder, ItemInstance itemInstance)
	{
		setCurrentIntention(IntentionType.CAST, skillUseHolder, itemInstance);
		notifyEvent(AiEventType.THINK, null, null);
	}
	
	@Override
	protected void onIntentionMoveTo(Location loc, Boat boat)
	{
		setCurrentIntention(IntentionType.MOVE_TO, loc, boat);
		notifyEvent(AiEventType.THINK, null, null);
	}
	
	@Override
	protected void onEvtFinishedAttack()
	{
		if (_nextIntention.isBlank())
			notifyEvent(AiEventType.THINK, null, null);
		else
			changeCurrentIntention(_nextIntention);
	}
	
	@Override
	protected void onEvtFinishedAttackBow()
	{
		if (!_nextIntention.isBlank())
			changeCurrentIntention(_nextIntention);
	}
	
	@Override
	protected void onEvtBowAttackReused()
	{
		if (_nextIntention.isBlank())
			notifyEvent(AiEventType.THINK, null, null);
	}
	
	@Override
	protected void onEvtFinishedCasting()
	{
		if (_nextIntention.isBlank())
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
		else
			changeCurrentIntention(_nextIntention);
	}
	
	@Override
	protected void onEvtArrived()
	{
		if (_currentIntention.getType() == IntentionType.FOLLOW)
			return;
		
		if (_nextIntention.isBlank())
		{
			if (_currentIntention.getType() == IntentionType.MOVE_TO)
				changeCurrentIntention(IntentionType.ACTIVE, null, null);
			else
				notifyEvent(AiEventType.THINK, null, null);
		}
		else
			changeCurrentIntention(_nextIntention);
	}
	
	@Override
	protected void onEvtArrivedBlocked(SpawnLocation loc)
	{
		if (_currentIntention.getType() == IntentionType.FOLLOW)
			return;
		
		if (_nextIntention.isBlank())
		{
			if (_currentIntention.getType() == IntentionType.MOVE_TO)
				changeCurrentIntention(IntentionType.ACTIVE, null, null);
			else
				notifyEvent(AiEventType.THINK, null, null);
		}
		else
			changeCurrentIntention(_nextIntention);
	}
	
	@Override
	protected void onEvtDead()
	{
		stopAITask();
		
		getActor().broadcastPacket(new Die(getActor()));
		
		stopAttackStance();
		
		changeCurrentIntention(IntentionType.IDLE, null, null);
	}
	
	@Override
	protected void onEvtTeleported()
	{
		changeCurrentIntention(IntentionType.IDLE, null, null);
	}
	
	@Override
	protected void thinkAttack()
	{
		if (getActor().denyAiAction() || getActor().isSitting())
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			clientActionFailed();
			return;
		}
		
		final Creature target = (Creature) _currentIntention.getFirstParameter();
		if (target == null || targetLost(target))
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			return;
		}
		
		boolean isShiftPressed = (boolean) _currentIntention.getSecondParameter();
		if (getActor().getMove().maybeMoveToPawn(target, getActor().getPhysicalAttackRange(), isShiftPressed))
		{
			if (isShiftPressed)
			{
				changeCurrentIntention(IntentionType.ACTIVE, null, null);
				clientActionFailed();
			}
			
			return;
		}
		
		getActor().getMove().stop();
		
		if ((getActor().getAttackType() == WeaponType.BOW && !getActor().getAttack().isBowAttackReused()) || getActor().getAttack().isAttackingNow())
		{
			setNextIntention(_currentIntention);
			return;
		}
		
		if (!getActor().getAttack().canDoAttack(target))
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			clientActionFailed();
			return;
		}
		
		getActor().getAttack().doAttack(target);
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
		if (target == null || targetLost(target))
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
		if (_actor.getMove().maybeMoveToPawn(target, skill.getCastRange(), isShiftPressed))
		{
			if (isShiftPressed)
				changeCurrentIntention(IntentionType.ACTIVE, null, null);
			
			return;
		}
		
		if (!getActor().getCast().canDoCast(skillUseHolder))
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			return;
		}
		
		if (skill.getHitTime() > 50)
			getActor().getMove().stop();
		
		getActor().getCast().doCast(skillUseHolder, null);
	}
	
	@Override
	protected void thinkMoveTo()
	{
		if (getActor().denyAiAction() || getActor().isMovementDisabled())
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			clientActionFailed();
			return;
		}
		
		final Location loc = (Location) _currentIntention.getFirstParameter();
		getActor().getMove().moveToLocation(loc);
	}
	
	@Override
	protected void thinkFollow()
	{
		if (getActor().denyAiAction() || getActor().isMovementDisabled())
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			clientActionFailed();
			return;
		}
		
		final boolean isShiftPressed = (boolean) _currentIntention.getSecondParameter();
		if (isShiftPressed)
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			clientActionFailed();
			return;
		}
		
		final Creature target = (Creature) _currentIntention.getFirstParameter();
		if (!getActor().getMove().canfollow(target))
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			return;
		}
		
		getActor().getMove().startFollow(target);
	}
	
	@Override
	protected void thinkInteract()
	{
		// Not all Creatures can INTERACT
	}
	
	@Override
	protected void thinkSit()
	{
		// Not all Creatures can SIT
	}
	
	@Override
	protected void thinkStand()
	{
		// Not all Creatures can STAND
	}
	
	@Override
	protected void onEvtSatDown(WorldObject target)
	{
		// Not all Creatures can SIT
	}
	
	@Override
	protected void onEvtStoodUp()
	{
		// Not all Creatures can STAND
	}
	
	@Override
	protected void onIntentionSit(WorldObject target)
	{
		// Not all Creatures can SIT
	}
	
	@Override
	protected void onIntentionStand()
	{
		// Not all Creatures can STAND
	}
	
	@Override
	protected void onIntentionPickUp(WorldObject object, boolean isShiftPressed)
	{
		// Not all Creatures can PICKUP
	}
	
	@Override
	protected void onIntentionInteract(WorldObject object, boolean isShiftPressed)
	{
		// Not all Creatures can INTERACT
	}
	
	@Override
	protected void onEvtAttacked(Creature attacker)
	{
		startAttackStance();
	}
	
	@Override
	protected void onEvtAggression(Creature target, int aggro)
	{
		// Not all Creatures can ATTACK
	}
	
	@Override
	protected void onEvtEvaded(Creature attacker)
	{
		// Not all Creatures have a behaviour after having evaded a shot
	}
	
	@Override
	protected void onIntentionUseItem(Integer objectId)
	{
		// Not all Creatures can USE_ITEM
	}
	
	@Override
	protected void onIntentionFakeDeath(boolean startFakeDeath)
	{
		// Not all Creatures can FAKE_DEATH
	}
	
	@Override
	protected void onEvtOwnerAttacked(Creature attacker)
	{
		// Not all Creatures have a behaviour after their owner has been attacked
	}
	
	@Override
	protected void onIntentionFollow(Creature target, boolean isShiftPressed)
	{
		// Not all Creatures can FOLLOW
	}
	
	@Override
	protected void onEvtCancel()
	{
		// Not all Creatures can CANCEL
	}
	
	@Override
	protected ItemInstance thinkPickUp()
	{
		return null;
	}
	
	@Override
	protected void thinkUseItem()
	{
		// Not all Creatures can USE_ITEM
	}
	
	public boolean getFollowStatus()
	{
		return false;
	}
	
	public void setFollowStatus(boolean followStatus)
	{
		// Not all Creatures can FOLLOW
	}
	
	public boolean canDoInteract(WorldObject target)
	{
		return false;
	}
	
	public boolean canAttemptInteract()
	{
		return false;
	}
}