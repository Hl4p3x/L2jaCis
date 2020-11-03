package net.sf.l2j.gameserver.model.actor.ai.type;

import net.sf.l2j.gameserver.enums.AiEventType;
import net.sf.l2j.gameserver.enums.IntentionType;
import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance.ItemLocation;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.skills.L2Skill;

public abstract class PlayableAI extends CreatureAI
{
	public PlayableAI(Playable playable)
	{
		super(playable);
	}
	
	@Override
	protected void onEvtFinishedCasting()
	{
		if (_nextIntention.isBlank())
		{
			if (_currentIntention.getType() == IntentionType.CAST)
			{
				final SkillUseHolder skillUseHolder = (SkillUseHolder) _currentIntention.getFirstParameter();
				final L2Skill skill = skillUseHolder.getSkill();
				final Creature target = skillUseHolder.getFinalTarget();
				
				if (skill.nextActionIsAttack() && target.canAttackingContinueBy(getActor()))
					changeCurrentIntention(IntentionType.ATTACK, target, skillUseHolder.isShiftPressed());
				else
					changeCurrentIntention(IntentionType.ACTIVE, null, null);
			}
			else
				// TODO This occurs with skills that change the AI of the caster in callSkill->useSkill (eg. SoE)
				changeCurrentIntention(IntentionType.ACTIVE, null, null);
		}
		else
			changeCurrentIntention(_nextIntention);
	}
	
	@Override
	protected void onEvtFinishedAttack()
	{
		if (_nextIntention.isBlank())
		{
			final Creature target = (Creature) _currentIntention.getFirstParameter();
			if (target.canAttackingContinueBy(getActor()))
				notifyEvent(AiEventType.THINK, null, null);
			else
				changeCurrentIntention(IntentionType.ACTIVE, null, null);
		}
		else
			changeCurrentIntention(_nextIntention);
	}
	
	@Override
	protected void onIntentionPickUp(WorldObject object, boolean isShiftPressed)
	{
		setCurrentIntention(IntentionType.PICK_UP, object, isShiftPressed);
		notifyEvent(AiEventType.THINK, null, null);
	}
	
	@Override
	protected void onIntentionAttack(Creature target, boolean isShiftPressed)
	{
		if (target instanceof Playable)
		{
			final Player targetPlayer = target.getActingPlayer();
			final Player actorPlayer = getActor().getActingPlayer();
			
			if (!target.isInsideZone(ZoneId.PVP))
			{
				if (targetPlayer.getProtectionBlessing() && (actorPlayer.getLevel() - targetPlayer.getLevel()) >= 10 && actorPlayer.getKarma() > 0)
				{
					actorPlayer.sendPacket(SystemMessageId.TARGET_IS_INCORRECT);
					clientActionFailed();
					return;
				}
				
				if (actorPlayer.getProtectionBlessing() && (targetPlayer.getLevel() - actorPlayer.getLevel()) >= 10 && targetPlayer.getKarma() > 0)
				{
					actorPlayer.sendPacket(SystemMessageId.TARGET_IS_INCORRECT);
					clientActionFailed();
					return;
				}
			}
			
			if (targetPlayer.isCursedWeaponEquipped() && actorPlayer.getLevel() <= 20)
			{
				actorPlayer.sendPacket(SystemMessageId.TARGET_IS_INCORRECT);
				clientActionFailed();
				return;
			}
			
			if (actorPlayer.isCursedWeaponEquipped() && targetPlayer.getLevel() <= 20)
			{
				actorPlayer.sendPacket(SystemMessageId.TARGET_IS_INCORRECT);
				clientActionFailed();
				return;
			}
		}
		super.onIntentionAttack(target, isShiftPressed);
	}
	
	@Override
	protected void onIntentionFollow(Creature target, boolean isShiftPressed)
	{
		setCurrentIntention(IntentionType.FOLLOW, target, isShiftPressed);
		notifyEvent(AiEventType.THINK, null, null);
	}
	
	@Override
	protected ItemInstance thinkPickUp()
	{
		if (getActor().denyAiAction() || getActor().isSitting())
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			clientActionFailed();
			return null;
		}
		
		final WorldObject target = (WorldObject) _currentIntention.getFirstParameter();
		if (!(target instanceof ItemInstance) || targetLost(target))
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			clientActionFailed();
			return null;
		}
		
		final ItemInstance item = (ItemInstance) target;
		if (item.getLocation() != ItemLocation.VOID)
		{
			changeCurrentIntention(IntentionType.ACTIVE, null, null);
			clientActionFailed();
			return null;
		}
		
		final boolean isShiftPressed = (boolean) _currentIntention.getSecondParameter();
		if (getActor().getMove().maybeMoveToPosition(target.getPosition(), 36, isShiftPressed))
		{
			if (isShiftPressed)
			{
				changeCurrentIntention(IntentionType.ACTIVE, null, null);
				clientActionFailed();
			}
			
			return null;
		}
		
		changeCurrentIntention(IntentionType.ACTIVE, null, null);
		clientActionFailed();
		
		getActor().getMove().stop();
		
		return item;
	}
	
	@Override
	public Playable getActor()
	{
		return (Playable) _actor;
	}
	
}