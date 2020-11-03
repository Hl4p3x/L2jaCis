package net.sf.l2j.gameserver.model.actor.cast;

import net.sf.l2j.commons.concurrent.ThreadPool;

import net.sf.l2j.gameserver.data.manager.CastleManager;
import net.sf.l2j.gameserver.data.manager.SevenSignsManager;
import net.sf.l2j.gameserver.enums.AiEventType;
import net.sf.l2j.gameserver.enums.CabalType;
import net.sf.l2j.gameserver.enums.GaugeColor;
import net.sf.l2j.gameserver.enums.SealType;
import net.sf.l2j.gameserver.enums.SiegeSide;
import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.enums.skills.SkillType;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.handler.ISkillHandler;
import net.sf.l2j.gameserver.handler.SkillHandler;
import net.sf.l2j.gameserver.model.WorldRegion;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.model.entity.Siege;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillLaunched;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.network.serverpackets.SetupGauge;
import net.sf.l2j.gameserver.network.serverpackets.StatusUpdate;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.skills.AbstractEffect;
import net.sf.l2j.gameserver.skills.Formulas;
import net.sf.l2j.gameserver.skills.L2Skill;
import net.sf.l2j.gameserver.skills.l2skills.L2SkillSiegeFlag;
import net.sf.l2j.gameserver.skills.l2skills.L2SkillSummon;

/**
 * This class groups all cast data related to a {@link Player}.
 */
public class PlayerCast extends PlayableCast
{
	public PlayerCast(Creature creature)
	{
		super(creature);
	}
	
	@Override
	public void doCast(SkillUseHolder skillUseHolder, ItemInstance itemInstance)
	{
		super.doCast(skillUseHolder, itemInstance);
		
		if (skillUseHolder.getSkill().getItemConsumeId() > 0)
			((Player) _creature).destroyItemByItemId("Consume", skillUseHolder.getSkill().getItemConsumeId(), skillUseHolder.getSkill().getItemConsume(), null, true);
		
		((Player) _creature).clearRecentFakeDeath();
	}
	
	@Override
	public void doFusionCasttimeCast(SkillUseHolder skillUseHolder)
	{
		final L2Skill skill = skillUseHolder.getSkill();
		final int reuseDelay = skill.getReuseDelay();
		final boolean skillMastery = Formulas.calcSkillMastery(_creature, skill);
		if (skillMastery)
		{
			if (_creature.getActingPlayer() != null)
				_creature.getActingPlayer().sendPacket(SystemMessageId.SKILL_READY_TO_USE_AGAIN);
		}
		else
		{
			if (reuseDelay > 30000)
				_creature.addTimeStamp(skill, reuseDelay);
			
			if (reuseDelay > 10)
				_creature.disableSkill(skill, reuseDelay);
		}
		
		final int initMpConsume = _creature.getStat().getMpInitialConsume(skill);
		if (initMpConsume > 0)
		{
			_creature.getStatus().reduceMp(initMpConsume);
			
			final StatusUpdate su = new StatusUpdate(_creature);
			su.addAttribute(StatusUpdate.CUR_MP, (int) _creature.getCurrentMp());
			_creature.sendPacket(su);
		}
		
		final Creature target = skillUseHolder.getFinalTarget();
		if (target != _creature)
			_creature.getPosition().setHeadingTo(target);
		
		_targets = new Creature[]
		{
			target
		};
		
		final int hitTime = skill.getHitTime();
		final int coolTime = skill.getCoolTime();
		final long castInterruptTime = System.currentTimeMillis() + hitTime - 200;
		setCastTask(skillUseHolder, hitTime, coolTime, castInterruptTime);
		
		if (skill.getSkillType() == SkillType.FUSION)
		{
			_creature.startFusionSkill(target, skill);
			target.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT).addSkillName(_skillUseHolder.getSkill()));
		}
		else
			callSkill(skill, _targets);
		
		_creature.broadcastPacket(new MagicSkillUse(_creature, target, skill.getId(), skill.getLevel(), hitTime, reuseDelay, false));
		_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.USE_S1).addSkillName(skill));
		_creature.sendPacket(new SetupGauge(GaugeColor.BLUE, _hitTime));
		
		_castTask = ThreadPool.schedule(() -> onMagicEffectHitTimer(), hitTime > 410 ? hitTime - 400 : 0);
	}
	
	public void onMagicEffectHitTimer()
	{
		_targets = _skillUseHolder.getSkill().isSingleTarget() ? new Creature[]
		{
			_skillUseHolder.getFinalTarget()
		} : _skillUseHolder.getTargetList();
		
		if (_creature.getFusionSkill() != null)
		{
			_creature.getFusionSkill().onCastAbort();
			_creature.notifyQuestEventSkillFinished(_skillUseHolder.getSkill(), _targets[0]);
			clearCastTask();
			return;
		}
		
		_creature.broadcastPacket(new MagicSkillLaunched(_creature, _skillUseHolder.getSkill().getId(), _skillUseHolder.getSkill().getLevel(), _targets));
		
		_creature.rechargeShots(_skillUseHolder.getSkill().useSoulShot(), _skillUseHolder.getSkill().useSpiritShot());
		
		final StatusUpdate su = new StatusUpdate(_creature);
		final double mpConsume = _creature.getStat().getMpConsume(_skillUseHolder.getSkill());
		if (mpConsume > 0)
		{
			if (mpConsume > _creature.getCurrentMp())
			{
				_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOT_ENOUGH_MP));
				stop();
				return;
			}
			
			_creature.getStatus().reduceMp(mpConsume);
			su.addAttribute(StatusUpdate.CUR_MP, (int) _creature.getCurrentMp());
			_creature.sendPacket(su);
		}
		
		_castTask = ThreadPool.schedule(() -> onMagicEffectFinalizer(), 400);
	}
	
	/*
	 * Runs after skill hitTime+coolTime
	 */
	public void onMagicEffectFinalizer()
	{
		_creature.rechargeShots(_skillUseHolder.getSkill().useSoulShot(), _skillUseHolder.getSkill().useSpiritShot());
		
		if (_skillUseHolder.getSkill().isOffensive() && _targets.length != 0)
			_creature.getAI().startAttackStance();
		
		final Creature target = _targets.length > 0 ? _targets[0] : _skillUseHolder.getFinalTarget();
		_creature.notifyQuestEventSkillFinished(_skillUseHolder.getSkill(), target);
		
		clearCastTask();
		_creature.getAI().notifyEvent(AiEventType.FINISHED_CASTING, null, null);
	}
	
	@Override
	public void doToggleCast(SkillUseHolder skillUseHolder)
	{
		setCastTask(skillUseHolder, 0, 0, 0);
		
		_creature.broadcastPacket(new MagicSkillUse(_creature, _creature, _skillUseHolder.getSkill().getId(), _skillUseHolder.getSkill().getLevel(), 0, 0));
		
		_targets = new Creature[]
		{
			_skillUseHolder.getFinalTarget()
		};
		
		// If the toggle is already active, we don't need to do anything else besides stopping it.
		final AbstractEffect effect = _creature.getFirstEffect(_skillUseHolder.getSkill().getId());
		if (effect != null)
			effect.exit();
		else
		{
			final StatusUpdate su = new StatusUpdate(_creature);
			final double mpConsume = _creature.getStat().getMpConsume(_skillUseHolder.getSkill());
			if (mpConsume > 0)
			{
				if (mpConsume > _creature.getCurrentMp())
				{
					_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOT_ENOUGH_MP));
					stop();
					return;
				}
				
				_creature.getStatus().reduceMp(mpConsume);
				su.addAttribute(StatusUpdate.CUR_MP, (int) _creature.getCurrentMp());
			}
			
			final double hpConsume = _skillUseHolder.getSkill().getHpConsume();
			if (hpConsume > 0)
			{
				if (hpConsume > _creature.getCurrentHp())
				{
					_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOT_ENOUGH_HP));
					stop();
					return;
				}
				
				_creature.getStatus().reduceHp(hpConsume, _creature, true);
				su.addAttribute(StatusUpdate.CUR_HP, (int) _creature.getCurrentHp());
			}
			
			if (mpConsume > 0 || hpConsume > 0)
				_creature.sendPacket(su);
			
			final ISkillHandler handler = SkillHandler.getInstance().getHandler(_skillUseHolder.getSkill().getSkillType());
			if (handler != null)
				handler.useSkill(_creature, _skillUseHolder.getSkill(), _targets);
			else
				_skillUseHolder.getSkill().useSkill(_creature, _targets);
		}
		
		_castTask = ThreadPool.schedule(() -> onMagicFinalizer(), 0);
	}
	
	@Override
	public boolean canAttemptCast(SkillUseHolder skillUseHolder)
	{
		if (!super.canAttemptCast(skillUseHolder))
			return false;
		
		final Player player = (Player) _creature;
		final L2Skill skill = skillUseHolder.getSkill();
		if (player.isWearingFormalWear())
		{
			player.sendPacket(SystemMessageId.CANNOT_USE_ITEMS_SKILLS_WITH_FORMALWEAR);
			return false;
		}
		
		final SkillType skillType = skill.getSkillType();
		if (player.isFishing() && (skillType != SkillType.PUMPING && skillType != SkillType.REELING && skillType != SkillType.FISHING))
		{
			player.sendPacket(SystemMessageId.ONLY_FISHING_SKILLS_NOW);
			return false;
		}
		
		if (player.isInObserverMode())
		{
			player.sendPacket(SystemMessageId.OBSERVERS_CANNOT_PARTICIPATE);
			return false;
		}
		
		if (player.isSitting() && !player.isFakeDeath())
		{
			player.sendPacket(SystemMessageId.CANT_MOVE_SITTING);
			return false;
		}
		
		if (player.isFakeDeath() && skill.getId() != 60)
		{
			player.sendPacket(SystemMessageId.CANT_MOVE_SITTING);
			return false;
		}
		
		final SkillTargetType skillTargetType = skill.getTargetType();
		final Location worldPosition = player.getCurrentSkillWorldPosition();
		if (skillTargetType == SkillTargetType.GROUND && worldPosition == null)
			return false;
		
		final Creature target = skillUseHolder.getFinalTarget();
		if (player.isInDuel())
		{
			if (target instanceof Playable)
			{
				final Player cha = target.getActingPlayer();
				if (cha.getDuelId() != player.getDuelId())
				{
					player.sendPacket(SystemMessageId.INVALID_TARGET);
					return false;
				}
			}
		}
		
		if (skill.isSiegeSummonSkill())
		{
			final Siege siege = CastleManager.getInstance().getActiveSiege(player);
			if (siege == null || !siege.checkSide(player.getClan(), SiegeSide.ATTACKER) || (player.isInSiege() && player.isInsideZone(ZoneId.CASTLE)))
			{
				player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOT_CALL_PET_FROM_THIS_LOCATION));
				return false;
			}
			
			if (SevenSignsManager.getInstance().isSealValidationPeriod() && SevenSignsManager.getInstance().getSealOwner(SealType.STRIFE) == CabalType.DAWN && SevenSignsManager.getInstance().getPlayerCabal(player.getObjectId()) == CabalType.DUSK)
			{
				player.sendPacket(SystemMessageId.SEAL_OF_STRIFE_FORBIDS_SUMMONING);
				return false;
			}
		}
		
		return true;
	}
	
	@Override
	public boolean canDoCast(SkillUseHolder skillUseHolder)
	{
		if (!super.canDoCast(skillUseHolder))
			return false;
		
		final Player player = (Player) _creature;
		final Creature target = skillUseHolder.getFinalTarget();
		final L2Skill skill = skillUseHolder.getSkill();
		if (!skill.checkCondition(player, target, false))
			return false;
		
		final SkillTargetType skillTargetType = skill.getTargetType();
		final Location worldPosition = player.getCurrentSkillWorldPosition();
		if (skillTargetType == SkillTargetType.GROUND)
		{
			if (!GeoEngine.getInstance().canSeeLocation(player, worldPosition))
			{
				player.sendPacket(SystemMessageId.CANT_SEE_TARGET);
				return false;
			}
		}
		
		final SkillType skillType = skill.getSkillType();
		switch (skillType)
		{
			case SUMMON:
				if (!((L2SkillSummon) skill).isCubic() && (player.getSummon() != null || player.isMounted()))
				{
					player.sendPacket(SystemMessageId.SUMMON_ONLY_ONE);
					return false;
				}
				break;
			
			case RESURRECT:
				final Siege siege = CastleManager.getInstance().getActiveSiege(player);
				if (siege != null)
				{
					if (player.getClan() == null)
					{
						player.sendPacket(SystemMessageId.CANNOT_BE_RESURRECTED_DURING_SIEGE);
						return false;
					}
					
					final SiegeSide side = siege.getSide(player.getClan());
					if (side == SiegeSide.DEFENDER || side == SiegeSide.OWNER)
					{
						if (siege.getControlTowerCount() == 0)
						{
							player.sendPacket(SystemMessageId.TOWER_DESTROYED_NO_RESURRECTION);
							return false;
						}
					}
					else if (side == SiegeSide.ATTACKER)
					{
						if (player.getClan().getFlag() == null)
						{
							player.sendPacket(SystemMessageId.NO_RESURRECTION_WITHOUT_BASE_CAMP);
							return false;
						}
					}
					else
					{
						player.sendPacket(SystemMessageId.CANNOT_BE_RESURRECTED_DURING_SIEGE);
						return false;
					}
				}
				break;
			
			case SIGNET:
			case SIGNET_CASTTIME:
				final WorldRegion region = player.getRegion();
				if (region == null)
					return false;
				
				if (!region.checkEffectRangeInsidePeaceZone(skill, skillTargetType == SkillTargetType.GROUND ? player.getCurrentSkillWorldPosition() : player.getPosition()))
				{
					player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED).addSkillName(skill));
					return false;
				}
				break;
			
			case SPOIL:
				if (!(target instanceof Monster))
				{
					player.sendPacket(SystemMessageId.INVALID_TARGET);
					return false;
				}
				break;
			
			case SWEEP:
				final int spoilerId = ((Monster) target).getSpoilState().getSpoilerId();
				if (spoilerId == 0)
				{
					player.sendPacket(SystemMessageId.SWEEPER_FAILED_TARGET_NOT_SPOILED);
					return false;
				}
				
				if (!player.isLooterOrInLooterParty(spoilerId))
				{
					player.sendPacket(SystemMessageId.SWEEP_NOT_ALLOWED);
					return false;
				}
				break;
			
			case DRAIN_SOUL:
				if (!(target instanceof Monster))
				{
					player.sendPacket(SystemMessageId.INVALID_TARGET);
					return false;
				}
				break;
			
			case TAKECASTLE:
				if (!player.checkIfOkToCastSealOfRule(CastleManager.getInstance().getCastle(player), false, skill, target))
					return false;
				
				break;
			
			case SIEGEFLAG:
				if (!L2SkillSiegeFlag.checkIfOkToPlaceFlag(player, false))
					return false;
				
				break;
			
			case STRSIEGEASSAULT:
				if (!player.checkIfOkToUseStriderSiegeAssault(skill))
					return false;
				
				break;
			
			case SUMMON_FRIEND:
				if (!(player.checkSummonerStatus() && player.checkSummonTargetStatus(target)))
					return false;
				
				break;
		}
		
		if (player.isInOlympiadMode() && (skill.isHeroSkill() || skillType == SkillType.RESURRECT))
		{
			player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.THIS_SKILL_IS_NOT_AVAILABLE_FOR_THE_OLYMPIAD_EVENT));
			return false;
		}
		
		if (skill.getItemConsumeId() > 0)
		{
			final ItemInstance requiredItems = player.getInventory().getItemByItemId(skill.getItemConsumeId());
			if (requiredItems == null || requiredItems.getCount() < skill.getItemConsume())
			{
				player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED).addSkillName(skill));
				return false;
			}
		}
		
		return true;
	}
}