package net.sf.l2j.gameserver.model.actor.cast;

import java.util.List;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.concurrent.ThreadPool;
import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.math.MathUtil;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.SkillTable.FrequentSkill;
import net.sf.l2j.gameserver.enums.AiEventType;
import net.sf.l2j.gameserver.enums.GaugeColor;
import net.sf.l2j.gameserver.enums.IntentionType;
import net.sf.l2j.gameserver.enums.ScriptEventType;
import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.enums.items.ShotType;
import net.sf.l2j.gameserver.enums.skills.EffectType;
import net.sf.l2j.gameserver.enums.skills.SkillType;
import net.sf.l2j.gameserver.enums.skills.Stats;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.handler.ISkillHandler;
import net.sf.l2j.gameserver.handler.SkillHandler;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.item.kind.Weapon;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillCanceled;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillLaunched;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.network.serverpackets.SetupGauge;
import net.sf.l2j.gameserver.network.serverpackets.StatusUpdate;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.scripting.Quest;
import net.sf.l2j.gameserver.skills.AbstractEffect;
import net.sf.l2j.gameserver.skills.Formulas;
import net.sf.l2j.gameserver.skills.L2Skill;

/**
 * This class groups all cast data related to a {@link Creature}.
 */
public class CreatureCast
{
	public static final CLogger LOGGER = new CLogger(CreatureCast.class.getName());
	
	protected final Creature _creature;
	protected long _castInterruptTime;
	protected volatile boolean _isCastingNow;
	
	protected Creature[] _targets;
	protected SkillUseHolder _skillUseHolder;
	protected int _hitTime;
	protected int _coolTime;
	
	protected ScheduledFuture<?> _castTask;
	
	public CreatureCast(Creature creature)
	{
		_creature = creature;
	}
	
	public final boolean canAbortCast()
	{
		return _castInterruptTime > System.currentTimeMillis();
	}
	
	public final boolean isCastingNow()
	{
		return _isCastingNow;
	}
	
	public final L2Skill getCurrentSkill()
	{
		if (_skillUseHolder != null)
			return _skillUseHolder.getSkill();
		
		return null;
	}
	
	public void doInstantCast(L2Skill itemSkill, ItemInstance item)
	{
		// Non-Playable Creatures cannot use potions or energy stones
	}
	
	public void doToggleCast(SkillUseHolder skillUseHolder)
	{
		// Non-Player Creatures cannot use TOGGLES
	}
	
	/**
	 * Manage the casting task (casting and interrupt time, re-use delay...) and display the casting bar and animation on client.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B>
	 * <ul>
	 * <li>Verify the possibilty of the the cast : skill is a spell, caster isn't muted...</li>
	 * <li>Get the list of all targets (ex : area effects) and define the L2Charcater targeted (its stats will be used in calculation)</li>
	 * <li>Calculate the casting time (base + modifier of MAtkSpd), interrupt time and re-use delay</li>
	 * <li>Send MagicSkillUse (to diplay casting animation), a packet SetupGauge (to display casting bar) and a system message</li>
	 * <li>Disable all skills during the casting time (create a task EnableAllSkills)</li>
	 * <li>Disable the skill during the re-use delay (create a task EnableSkill)</li>
	 * <li>Create a task CastTask (that will call method onMagicUseTimer) to launch the Magic Skill at the end of the casting time</li>
	 * </ul>
	 * @param skillUseHolder
	 * @param itemInstance
	 */
	public void doCast(SkillUseHolder skillUseHolder, ItemInstance itemInstance)
	{
		final L2Skill skill = skillUseHolder.getSkill();
		int hitTime = skill.getHitTime();
		int coolTime = skill.getCoolTime();
		if (!skill.isStaticHitTime())
		{
			hitTime = Formulas.calcAtkSpd(_creature, skill, hitTime);
			if (coolTime > 0)
				coolTime = Formulas.calcAtkSpd(_creature, skill, coolTime);
			
			if (skill.isMagic() && (_creature.isChargedShot(ShotType.SPIRITSHOT) || _creature.isChargedShot(ShotType.BLESSED_SPIRITSHOT)))
			{
				hitTime = (int) (0.70 * hitTime);
				coolTime = (int) (0.70 * coolTime);
			}
			
			if (skill.getHitTime() >= 500 && hitTime < 500)
				hitTime = 500;
		}
		
		int reuseDelay = skill.getReuseDelay();
		if (!skill.isStaticReuse())
		{
			reuseDelay *= _creature.calcStat(skill.isMagic() ? Stats.MAGIC_REUSE_RATE : Stats.P_REUSE, 1, null, null);
			reuseDelay *= 333.0 / (skill.isMagic() ? _creature.getMAtkSpd() : _creature.getPAtkSpd());
		}
		
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
		
		_creature.broadcastPacket(new MagicSkillUse(_creature, target, skill.getId(), skill.getLevel(), hitTime, reuseDelay, false));
		_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.USE_S1).addSkillName(skill));
		
		final long castInterruptTime = System.currentTimeMillis() + hitTime - 200;
		setCastTask(skillUseHolder, hitTime, coolTime, castInterruptTime);
		
		if (_hitTime > 410)
		{
			if (_creature instanceof Player)
				_creature.sendPacket(new SetupGauge(GaugeColor.BLUE, _hitTime));
		}
		else
			_hitTime = 0;
		
		_castTask = ThreadPool.schedule(() -> onMagicLaunchedTimer(), hitTime > 410 ? hitTime - 400 : 0);
	}
	
	/**
	 * Manage the magic skill launching task (MP, HP, Item consummation...) and display the magic skill animation on client.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B>
	 * <ul>
	 * <li>Broadcast MagicSkillLaunched packet (to display magic skill animation)</li>
	 * <li>Consumme MP, HP and Item if necessary</li>
	 * <li>Send StatusUpdate with MP modification to the Player</li>
	 * <li>Launch the magic skill in order to calculate its effects</li>
	 * <li>If the skill type is PDAM, notify the AI of the target with ATTACK</li>
	 * <li>Notify the AI of the Creature with EVT_FINISHED_CASTING</li>
	 * </ul>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : A magic skill casting MUST BE in progress</B></FONT>
	 */
	public void onMagicLaunchedTimer()
	{
		// No checks for range, LoS, PEACE if the target is the caster
		if (_skillUseHolder.getFinalTarget() != _creature)
		{
			int escapeRange = 0;
			if (_skillUseHolder.getSkill().getEffectRange() > 0)
				escapeRange = _skillUseHolder.getSkill().getEffectRange();
			else if (_skillUseHolder.getSkill().getCastRange() <= 0 && _skillUseHolder.getSkill().getSkillRadius() > 80)
				escapeRange = _skillUseHolder.getSkill().getSkillRadius();
			
			// If the mainTarget (not self) dies or disappears
			if ((_skillUseHolder.getFinalTarget().isDead() && !_skillUseHolder.getSkill().canTargetCorpse()) || !_creature.knows(_skillUseHolder.getFinalTarget()))
			{
				stop();
				return;
			}
			
			// If the mainTarget (not self) has gone out of range
			if (escapeRange > 0 && !MathUtil.checkIfInRange(escapeRange, _creature, _skillUseHolder.getFinalTarget(), true))
			{
				_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.DIST_TOO_FAR_CASTING_STOPPED));
				
				stop();
				return;
			}
			
			// If the mainTarget (not self) has gone out of sight
			if (_skillUseHolder.getSkill().getSkillRadius() > 0 && !GeoEngine.getInstance().canSeeTarget(_creature, _skillUseHolder.getFinalTarget()))
			{
				_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANT_SEE_TARGET));
				
				stop();
				return;
			}
			
			// If the mainTarget (not self) has gone into a PEACE zone
			if (_skillUseHolder.getSkill().isOffensive() && _creature instanceof Playable && _skillUseHolder.getFinalTarget() instanceof Playable && _skillUseHolder.getFinalTarget().isInsideZone(ZoneId.PEACE) && !_creature.getActingPlayer().getAccessLevel().allowPeaceAttack())
			{
				_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TARGET_IN_PEACEZONE));
				
				stop();
				return;
			}
		}
		
		_targets = _skillUseHolder.getSkill().isSingleTarget() ? new Creature[]
		{
			_skillUseHolder.getFinalTarget()
		} : _skillUseHolder.getTargetList();
		
		_creature.broadcastPacket(new MagicSkillLaunched(_creature, _skillUseHolder.getSkill().getId(), _skillUseHolder.getSkill().getLevel(), _targets));
		
		_castTask = ThreadPool.schedule(() -> onMagicHitTimer(), _hitTime == 0 ? 0 : 400);
	}
	
	/*
	 * Runs in the end of skill casting
	 */
	public void onMagicHitTimer()
	{
		final StatusUpdate su = new StatusUpdate(_creature);
		boolean isSendStatus = false;
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
			isSendStatus = true;
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
			isSendStatus = true;
		}
		
		if (isSendStatus)
			_creature.sendPacket(su);
		
		if (_creature instanceof Player && _skillUseHolder.getSkill().getNumCharges() > 0)
		{
			if (_skillUseHolder.getSkill().getMaxCharges() > 0)
				((Player) _creature).increaseCharges(_skillUseHolder.getSkill().getNumCharges(), _skillUseHolder.getSkill().getMaxCharges());
			else
				((Player) _creature).decreaseCharges(_skillUseHolder.getSkill().getNumCharges());
		}
		
		callSkill(_skillUseHolder.getSkill(), _targets);
		
		for (final Creature target : _targets)
		{
			if (!(target instanceof Playable))
				continue;
			
			// TODO check the sendPacket override in Summon. Is it correct/necessary?
			if (target instanceof Player && _skillUseHolder.getSkill().getSkillType() == SkillType.BUFF || _skillUseHolder.getSkill().getSkillType() == SkillType.SEED)
				target.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT).addSkillName(_skillUseHolder.getSkill()));
			
			if (_creature instanceof Player && target instanceof Summon)
				((Summon) target).updateAndBroadcastStatus(1);
		}
		
		_castTask = ThreadPool.schedule(() -> onMagicFinalizer(), (_hitTime == 0 || _coolTime == 0) ? 0 : _coolTime);
	}
	
	/*
	 * Runs after skill hitTime+coolTime
	 */
	public void onMagicFinalizer()
	{
		if (_skillUseHolder.getSkill() == null)
			return;
		
		_creature.rechargeShots(_skillUseHolder.getSkill().useSoulShot(), _skillUseHolder.getSkill().useSpiritShot());
		
		if (_skillUseHolder.getSkill().isOffensive() && _targets.length != 0)
			_creature.getAI().startAttackStance();
		
		final Creature target = _targets.length > 0 ? _targets[0] : _skillUseHolder.getFinalTarget();
		_creature.notifyQuestEventSkillFinished(_skillUseHolder.getSkill(), target);
		
		clearCastTask();
		_creature.getAI().notifyEvent(AiEventType.FINISHED_CASTING, null, null);
	}
	
	/**
	 * Check if attempting to cast of skill is possible. BEFORE MOVEMENT.
	 * @param skillUseHolder
	 * @return True if casting is possible
	 */
	public boolean canAttemptCast(SkillUseHolder skillUseHolder)
	{
		final L2Skill skill = skillUseHolder.getSkill();
		if (_creature.isSkillDisabled(skill))
		{
			_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_PREPARED_FOR_REUSE).addSkillName(skill));
			return false;
		}
		
		return true;
	}
	
	/**
	 * Check if casting of skill is possible. AFTER MOVEMENT.
	 * @param skillUseHolder
	 * @return True if casting is possible
	 */
	public boolean canDoCast(SkillUseHolder skillUseHolder)
	{
		final L2Skill skill = skillUseHolder.getSkill();
		if (_creature.getCurrentMp() < _creature.getStat().getMpConsume(skill) + _creature.getStat().getMpInitialConsume(skill))
		{
			_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOT_ENOUGH_MP));
			_creature.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		if (_creature.getCurrentHp() <= skill.getHpConsume())
		{
			_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOT_ENOUGH_HP));
			_creature.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		if ((skill.isMagic() && _creature.isMuted()) || (!skill.isMagic() && _creature.isPhysicalMuted()))
		{
			_creature.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		final Creature target = skillUseHolder.getFinalTarget();
		if (skill.getCastRange() > 0 && !GeoEngine.getInstance().canSeeTarget(_creature, target))
		{
			_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANT_SEE_TARGET));
			_creature.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		if (!skill.getWeaponDependancy(_creature))
		{
			_creature.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		return true;
	}
	
	/**
	 * Abort the cast of the Creature and send Server->Client MagicSkillCanceld/ActionFailed packet.<BR>
	 * <BR>
	 */
	public final void stop()
	{
		if (_creature.getFusionSkill() != null)
			_creature.getFusionSkill().onCastAbort();
		
		final AbstractEffect effect = _creature.getFirstEffect(EffectType.SIGNET_GROUND);
		if (effect != null)
			effect.exit();
		
		if (_creature.isAllSkillsDisabled())
			_creature.enableAllSkills();
		
		_creature.broadcastPacket(new MagicSkillCanceled(_creature.getObjectId()));
		
		clearCastTask();
		
		if (_castTask != null)
		{
			_castTask.cancel(false);
			_castTask = null;
		}
		_creature.getAI().tryTo(IntentionType.ACTIVE, null, null);
		_creature.getAI().clientActionFailed();
	}
	
	/**
	 * Break a cast and send Server->Client ActionFailed packet and a System Message to the Creature.
	 */
	public void interrupt()
	{
		if (canAbortCast())
		{
			stop();
			_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CASTING_INTERRUPTED));
		}
	}
	
	/**
	 * Launch the magic skill and calculate its effects on each target contained in the targets table.
	 * @param skill The L2Skill to use
	 * @param targets The table of Creature targets
	 */
	public void callSkill(L2Skill skill, Creature[] targets)
	{
		try
		{
			for (final Creature target : targets)
			{
				if (_creature instanceof Playable)
				{
					if (!Config.RAID_DISABLE_CURSE && target.isRaidRelated() && _creature.getLevel() > target.getLevel() + 8)
					{
						final L2Skill curse = FrequentSkill.RAID_CURSE.getSkill();
						if (curse != null)
						{
							_creature.broadcastPacket(new MagicSkillUse(_creature, _creature, curse.getId(), curse.getLevel(), 300, 0));
							curse.getEffects(_creature, _creature);
						}
						return;
					}
					
					if (skill.isOverhit() && target instanceof Monster)
						((Monster) target).getOverhitState().set(true);
				}
				
				switch (skill.getSkillType())
				{
					case COMMON_CRAFT:
					case DWARVEN_CRAFT:
						break;
					
					default:
						final Weapon activeWeaponItem = _creature.getActiveWeaponItem();
						if (activeWeaponItem != null && !target.isDead())
							activeWeaponItem.castSkillOnMagic(_creature, target, skill);
						
						if (_creature.getChanceSkills() != null)
							_creature.getChanceSkills().onSkillHit(target, false, skill.isMagic(), skill.isOffensive());
						
						if (target.getChanceSkills() != null)
							target.getChanceSkills().onSkillHit(_creature, true, skill.isMagic(), skill.isOffensive());
				}
			}
			
			final ISkillHandler handler = SkillHandler.getInstance().getHandler(skill.getSkillType());
			if (handler != null)
				handler.useSkill(_creature, skill, targets);
			else
				skill.useSkill(_creature, targets);
			
			final Player player = _creature.getActingPlayer();
			if (player != null)
			{
				for (final Creature target : targets)
				{
					if (skill.isOffensive())
					{
						if (player.getSummon() != target)
							player.updatePvPStatus(target);
					}
					else
					{
						if (target instanceof Player)
						{
							if (!(target.equals(_creature) || target.equals(player)) && (((Player) target).getPvpFlag() > 0 || ((Player) target).getKarma() > 0))
								player.updatePvPStatus();
						}
						else if (target instanceof Attackable && !((Attackable) target).isGuard())
						{
							switch (skill.getSkillType())
							{
								case SUMMON:
								case BEAST_FEED:
								case UNLOCK:
								case UNLOCK_SPECIAL:
								case DELUXE_KEY_UNLOCK:
									break;
								
								default:
									player.updatePvPStatus();
							}
						}
					}
					
					switch (skill.getTargetType())
					{
						case CORPSE_MOB:
						case AREA_CORPSE_MOB:
						case AURA_CORPSE_MOB:
							if (target.isDead())
								((Npc) target).endDecayTask();
							break;
						default:
							break;
					}
				}
				
				for (final Npc npcMob : player.getKnownTypeInRadius(Npc.class, 1000))
				{
					final List<Quest> scripts = npcMob.getTemplate().getEventQuests(ScriptEventType.ON_SKILL_SEE);
					if (scripts != null)
						for (final Quest quest : scripts)
							quest.notifySkillSee(npcMob, player, skill, targets, _creature instanceof Summon);
				}
			}
			
			if (skill.isOffensive())
			{
				switch (skill.getSkillType())
				{
					case AGGREDUCE:
					case AGGREMOVE:
					case AGGREDUCE_CHAR:
						break;
					
					default:
						for (final Creature target : targets)
						{
							if (target != null && target.hasAI())
								target.getAI().notifyEvent(AiEventType.ATTACKED, _creature, null);
						}
						break;
				}
			}
		}
		catch (final Exception e)
		{
			LOGGER.error("Couldn't call skill {}.", e, (skill == null) ? "not found" : skill.getId());
		}
	}
	
	protected void clearCastTask()
	{
		_targets = null;
		_skillUseHolder = null;
		_hitTime = 0;
		_coolTime = 0;
		_castInterruptTime = 0;
		_isCastingNow = false;
	}
	
	protected void setCastTask(SkillUseHolder skillUseHolder, int hitTime, int coolTime, long castInterruptTime)
	{
		_skillUseHolder = skillUseHolder;
		_hitTime = hitTime;
		_coolTime = coolTime;
		_castInterruptTime = castInterruptTime;
		_isCastingNow = true;
	}
	
	/**
	 * Use: FUSION and SIGNET_CASTTIME skils
	 * @param skillUseHolder
	 */
	public void doFusionCasttimeCast(SkillUseHolder skillUseHolder)
	{
		// Non-Player Creatures cannot use FUSION or SIGNETS
	}
}