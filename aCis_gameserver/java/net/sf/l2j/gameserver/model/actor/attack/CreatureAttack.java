package net.sf.l2j.gameserver.model.actor.attack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.concurrent.ThreadPool;
import net.sf.l2j.commons.logging.CLogger;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.SkillTable.FrequentSkill;
import net.sf.l2j.gameserver.enums.AiEventType;
import net.sf.l2j.gameserver.enums.GaugeColor;
import net.sf.l2j.gameserver.enums.IntentionType;
import net.sf.l2j.gameserver.enums.ScriptEventType;
import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.enums.items.ShotType;
import net.sf.l2j.gameserver.enums.items.WeaponType;
import net.sf.l2j.gameserver.enums.skills.Stats;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.container.creature.ChanceSkillList;
import net.sf.l2j.gameserver.model.item.kind.Armor;
import net.sf.l2j.gameserver.model.item.kind.Weapon;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.Attack;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.network.serverpackets.SetupGauge;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.scripting.Quest;
import net.sf.l2j.gameserver.skills.Formulas;
import net.sf.l2j.gameserver.skills.L2Skill;

/**
 * This class groups all attack data related to a {@link Creature}.
 */
public class CreatureAttack
{
	public static final CLogger LOGGER = new CLogger(CreatureAttack.class.getName());
	
	protected final Creature _creature;
	protected volatile boolean _isAttackingNow;
	protected volatile boolean _isBowCoolingDown;
	
	protected HitHolder[] _hitHolders;
	protected WeaponType _weaponType;
	protected int _afterAttackDelay;
	protected boolean _isBow;
	
	protected ScheduledFuture<?> _attackTask;
	
	public CreatureAttack(Creature creature)
	{
		_creature = creature;
	}
	
	public boolean isAttackingNow()
	{
		return _isAttackingNow;
	}
	
	public boolean isBowAttackReused()
	{
		return !_isBowCoolingDown;
	}
	
	/**
	 * @param target The target to check
	 * @return True if the attacker doesn't have isAttackingDisabled
	 */
	public boolean canDoAttack(Creature target)
	{
		if (_creature.isAttackingDisabled())
			return false;
		
		if (!target.isAttackableBy(_creature) || !_creature.knows(target))
			return false;
		
		if (!GeoEngine.getInstance().canSeeTarget(_creature, target))
		{
			_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANT_SEE_TARGET));
			return false;
		}
		
		return true;
	}
	
	/**
	 * Manage hit process (called by Hit Task).<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B>
	 * <ul>
	 * <li>If the attacker/target is dead or use fake death, notify the AI with EVT_CANCEL and send ActionFailed (if attacker is a Player)</li>
	 * <li>If attack isn't aborted, send a message system (critical hit, missed...) to attacker/target if they are Player</li>
	 * <li>If attack isn't aborted and hit isn't missed, reduce HP of the target and calculate reflection damage to reduce HP of attacker if necessary</li>
	 * <li>if attack isn't aborted and hit isn't missed, manage attack or cast break of the target (calculating rate, sending message...)</li>
	 * </ul>
	 */
	private void onHitTimer()
	{
		// Somethng happens to the target between the attacker attacking and the actual damage being dealt.
		// There is no PEACE zone check here. If the attack starts outside and in the meantime the mainTarget walks into a PEACE zone, it gets hit.
		final Creature mainTarget = _hitHolders[0]._target;
		if (mainTarget.isDead() || !_creature.knows(mainTarget))
		{
			stop();
			return;
		}
		
		final Player player = _creature.getActingPlayer();
		if (player != null && player.getSummon() != mainTarget)
			player.updatePvPStatus(mainTarget);
		
		_creature.rechargeShots(true, false);
		
		switch (_weaponType)
		{
			case DUAL:
				doHit(_hitHolders[0]);
				
				_attackTask = ThreadPool.schedule(() ->
				{
					doHit(_hitHolders[1]);
					
					_attackTask = ThreadPool.schedule(() -> onFinishedAttack(), _afterAttackDelay);
				}, _afterAttackDelay);
				
				break;
			case POLE:
				for (HitHolder hitHolder : _hitHolders)
					doHit(hitHolder);
				
				_attackTask = ThreadPool.schedule(() -> onFinishedAttack(), _afterAttackDelay);
				break;
			case BOW:
				doHit(_hitHolders[0]);
				
				_attackTask = ThreadPool.schedule(() -> onFinishedAttackBow(), 0);
				break;
			default:
				doHit(_hitHolders[0]);
				
				_attackTask = ThreadPool.schedule(() -> onFinishedAttack(), _afterAttackDelay);
				break;
		}
	}
	
	private void onFinishedAttackBow()
	{
		_attackTask = ThreadPool.schedule(() ->
		{
			_isBowCoolingDown = false;
			_creature.getAI().notifyEvent(AiEventType.BOW_ATTACK_REUSED, null, null);
			
		}, _afterAttackDelay);
		
		clearAttackTask();
		_creature.getAI().notifyEvent(AiEventType.FINISHED_ATTACK_BOW, null, null);
	}
	
	private void onFinishedAttack()
	{
		clearAttackTask();
		_creature.getAI().notifyEvent(AiEventType.FINISHED_ATTACK, null, null);
	}
	
	private void doHit(HitHolder hitHolder)
	{
		final Creature target = hitHolder._target;
		if (hitHolder._miss)
		{
			if (target.hasAI())
				target.getAI().notifyEvent(AiEventType.EVADED, _creature, null);
			
			if (target.getChanceSkills() != null)
				target.getChanceSkills().onEvadedHit(_creature);
			
			if (target instanceof Player)
				target.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.AVOIDED_S1_ATTACK).addCharName(_creature));
		}
		
		_creature.sendDamageMessage(target, hitHolder._damage, false, hitHolder._crit, hitHolder._miss);
		
		// Character will be petrified if attacking a raid related object that's more than 8 levels lower
		if (!Config.RAID_DISABLE_CURSE && target.isRaidRelated() && _creature.getLevel() > target.getLevel() + 8)
		{
			final L2Skill skill = FrequentSkill.RAID_CURSE2.getSkill();
			if (skill != null)
			{
				// Send visual and skill effects. Caster is the victim.
				_creature.broadcastPacket(new MagicSkillUse(_creature, _creature, skill.getId(), skill.getLevel(), 300, 0));
				skill.getEffects(_creature, _creature);
			}
			
			hitHolder._damage = 0; // prevents messing up drop calculation
		}
		
		if (!hitHolder._miss && hitHolder._damage > 0)
		{
			_creature.getAI().startAttackStance();
			
			if (target.hasAI())
				target.getAI().notifyEvent(AiEventType.ATTACKED, _creature, null);
			
			int reflectedDamage = 0;
			
			// Reflect damage system - do not reflect if weapon is a bow or target is invulnerable
			if (!_isBow && !target.isInvul())
			{
				// quick fix for no drop from raid if boss attack high-level char with damage reflection
				if (!target.isRaidRelated() || _creature.getActingPlayer() == null || _creature.getActingPlayer().getLevel() <= target.getLevel() + 8)
				{
					// Calculate reflection damage to reduce HP of attacker if necessary
					final double reflectPercent = target.getStat().calcStat(Stats.REFLECT_DAMAGE_PERCENT, 0, null, null);
					if (reflectPercent > 0)
					{
						reflectedDamage = (int) (reflectPercent / 100. * hitHolder._damage);
						
						if (reflectedDamage > target.getMaxHp())
							reflectedDamage = target.getMaxHp();
					}
				}
			}
			
			// Reduce target HPs
			target.reduceCurrentHp(hitHolder._damage, _creature, null);
			
			// Reduce attacker HPs in case of a reflect.
			if (reflectedDamage > 0)
				_creature.reduceCurrentHp(reflectedDamage, target, true, false, null);
			
			if (!_isBow) // Do not absorb if weapon is of type bow
			{
				// Absorb HP from the damage inflicted
				final double absorbPercent = _creature.getStat().calcStat(Stats.ABSORB_DAMAGE_PERCENT, 0, null, null);
				
				if (absorbPercent > 0)
				{
					final int maxCanAbsorb = (int) (_creature.getMaxHp() - _creature.getCurrentHp());
					int absorbDamage = (int) (absorbPercent / 100. * hitHolder._damage);
					
					if (absorbDamage > maxCanAbsorb)
						absorbDamage = maxCanAbsorb; // Can't absord more than max hp
						
					if (absorbDamage > 0)
						_creature.setCurrentHp(_creature.getCurrentHp() + absorbDamage);
				}
			}
			
			// Manage cast break of the target (calculating rate, sending message...)
			Formulas.calcCastBreak(target, hitHolder._damage);
			
			// Maybe launch chance skills on us
			final ChanceSkillList chanceSkills = _creature.getChanceSkills();
			if (chanceSkills != null)
			{
				chanceSkills.onHit(target, false, hitHolder._crit);
				
				// Reflect triggers onHit
				if (reflectedDamage > 0)
					chanceSkills.onHit(target, true, false);
			}
			
			// Maybe launch chance skills on target
			if (target.getChanceSkills() != null)
				target.getChanceSkills().onHit(_creature, true, hitHolder._crit);
			
			// Launch weapon Special ability effect if available
			if (hitHolder._crit)
			{
				final Weapon activeWeapon = _creature.getActiveWeaponItem();
				if (activeWeapon != null)
					activeWeapon.castSkillOnCrit(_creature, target);
			}
		}
	}
	
	/**
	 * Launch a physical attack against a target (Simple, Bow, Pole or Dual).<BR>
	 * <BR>
	 * <B><U>Actions</U> :</B>
	 * <ul>
	 * <li>No checks are performed in this function.</li>
	 * <li>Get the active weapon (always equipped in the right hand)</li>
	 * <li>Calls the apropriate doAttackBy {@link WeaponType} function to schedule the attack</li>
	 * </ul>
	 * <ul>
	 * </ul>
	 * @param target
	 */
	public void doAttack(Creature target)
	{
		// Get the Attack Speed of the Creature (delay (in milliseconds) before next attack)
		final int timeAtk = Formulas.calculateTimeBetweenAttacks(_creature);
		final Weapon weaponItem = _creature.getActiveWeaponItem();
		final Attack attack = new Attack(_creature, _creature.isChargedShot(ShotType.SOULSHOT), (weaponItem != null) ? weaponItem.getCrystalType().getId() : 0);
		
		_creature.getPosition().setHeadingTo(target);
		
		boolean hitted;
		final WeaponType weaponItemType = _creature.getAttackType();
		switch (weaponItemType)
		{
			case BOW:
				hitted = doAttackHitByBow(attack, target, timeAtk, weaponItem);
				break;
			
			case POLE:
				hitted = doAttackHitByPole(attack, target, timeAtk / 2);
				break;
			
			case DUAL:
			case DUALFIST:
				hitted = doAttackHitByDual(attack, target, timeAtk / 2);
				break;
			
			case FIST:
				hitted = (_creature.getSecondaryWeaponItem() instanceof Armor) ? doAttackHitSimple(attack, target, timeAtk / 2) : doAttackHitByDual(attack, target, timeAtk / 2);
				break;
			
			default:
				hitted = doAttackHitSimple(attack, target, timeAtk / 2);
				break;
		}
		
		// Check if hit isn't missed
		if (hitted)
		{
			// IA implementation for ON_ATTACK_ACT (mob which attacks a player).
			if (_creature instanceof Attackable)
			{
				// Bypass behavior if the victim isn't a player
				final Player victim = target.getActingPlayer();
				if (victim != null)
				{
					final Npc mob = ((Npc) _creature);
					
					final List<Quest> scripts = mob.getTemplate().getEventQuests(ScriptEventType.ON_ATTACK_ACT);
					if (scripts != null)
						for (final Quest quest : scripts)
							quest.notifyAttackAct(mob, victim);
				}
			}
			
			// If we didn't miss the hit, discharge the shoulshots, if any
			_creature.setChargedShot(ShotType.SOULSHOT, false);
			
			final Player player = _creature.getActingPlayer();
			if (player != null)
			{
				if (player.isCursedWeaponEquipped())
				{
					// If hitted by a cursed weapon, Cp is reduced to 0
					if (!target.isInvul())
						target.setCurrentCp(0);
				}
				else if (player.isHero())
				{
					if (target instanceof Player && ((Player) target).isCursedWeaponEquipped())
						// If a cursed weapon is hitted by a Hero, Cp is reduced to 0
						target.setCurrentCp(0);
				}
			}
		}
		
		// If the Server->Client packet Attack contains at least 1 hit, send the Server->Client packet Attack
		// to the Creature AND to all Player in the _KnownPlayers of the Creature
		if (attack.hasHits())
			_creature.broadcastPacket(attack);
	}
	
	/**
	 * Launch a Bow attack.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B>
	 * <ul>
	 * <li>Calculate if hit is missed or not</li>
	 * <li>Consumme arrows</li>
	 * <li>If hit isn't missed, calculate if shield defense is efficient</li>
	 * <li>If hit isn't missed, calculate if hit is critical</li>
	 * <li>If hit isn't missed, calculate physical damages</li>
	 * <li>If the Creature is a Player, Send SetupGauge</li>
	 * <li>Create a new hit task with Medium priority</li>
	 * <li>Calculate and set the disable delay of the bow in function of the Attack Speed</li>
	 * <li>Add this hit to the Server-Client packet Attack</li>
	 * </ul>
	 * @param attack Server->Client packet Attack in which the hit will be added
	 * @param target The Creature targeted
	 * @param sAtk The Attack Speed of the attacker
	 * @param weapon The weapon, which is attacker using
	 * @return True if the hit isn't missed
	 */
	private boolean doAttackHitByBow(Attack attack, Creature target, int sAtk, Weapon weapon)
	{
		int damage1 = 0;
		byte shld1 = 0;
		boolean crit1 = false;
		
		_creature.reduceArrowCount();
		_creature.getStatus().reduceMp(_creature.getActiveWeaponItem().getMpConsume());
		
		final boolean miss1 = Formulas.calcHitMiss(_creature, target);
		if (!miss1)
		{
			shld1 = Formulas.calcShldUse(_creature, target, null);
			crit1 = Formulas.calcCrit(_creature.getStat().getCriticalHit(target, null));
			damage1 = (int) Formulas.calcPhysDam(_creature, target, null, shld1, crit1, attack.soulshot);
		}
		
		int reuse = weapon.getReuseDelay();
		if (reuse != 0)
			reuse = (reuse * 345) / _creature.getStat().getPAtkSpd();
		
		setAttackTask(new HitHolder[]
		{
			new HitHolder(target, damage1, crit1, miss1, shld1)
		}, WeaponType.BOW, reuse);
		
		_attackTask = ThreadPool.schedule(() -> onHitTimer(), sAtk);
		
		if (_creature instanceof Player)
		{
			_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.GETTING_READY_TO_SHOOT_AN_ARROW));
			_creature.sendPacket(new SetupGauge(GaugeColor.RED, sAtk + reuse));
		}
		attack.hit(attack.createHit(target, damage1, miss1, crit1, shld1));
		
		return !miss1;
	}
	
	/**
	 * Launch a Dual attack.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B>
	 * <ul>
	 * <li>Calculate if hits are missed or not</li>
	 * <li>If hits aren't missed, calculate if shield defense is efficient</li>
	 * <li>If hits aren't missed, calculate if hit is critical</li>
	 * <li>If hits aren't missed, calculate physical damages</li>
	 * <li>Create 2 new hit tasks with Medium priority</li>
	 * <li>Add those hits to the Server-Client packet Attack</li>
	 * </ul>
	 * @param attack Server->Client packet Attack in which the hit will be added
	 * @param target The Creature targeted
	 * @param sAtk The Attack Speed of the attacker
	 * @return True if hit 1 or hit 2 isn't missed
	 */
	private boolean doAttackHitByDual(Attack attack, Creature target, int sAtk)
	{
		int damage1 = 0;
		int damage2 = 0;
		byte shld1 = 0;
		byte shld2 = 0;
		boolean crit1 = false;
		boolean crit2 = false;
		
		final boolean miss1 = Formulas.calcHitMiss(_creature, target);
		if (!miss1)
		{
			shld1 = Formulas.calcShldUse(_creature, target, null);
			crit1 = Formulas.calcCrit(_creature.getStat().getCriticalHit(target, null));
			damage1 = (int) Formulas.calcPhysDam(_creature, target, null, shld1, crit1, attack.soulshot);
			damage1 /= 2;
		}
		
		final boolean miss2 = Formulas.calcHitMiss(_creature, target);
		if (!miss2)
		{
			shld2 = Formulas.calcShldUse(_creature, target, null);
			crit2 = Formulas.calcCrit(_creature.getStat().getCriticalHit(target, null));
			damage2 = (int) Formulas.calcPhysDam(_creature, target, null, shld2, crit2, attack.soulshot);
			damage2 /= 2;
		}
		
		setAttackTask(new HitHolder[]
		{
			new HitHolder(target, damage1, crit1, miss1, shld1),
			new HitHolder(target, damage2, crit2, miss2, shld2)
		}, WeaponType.DUAL, sAtk / 2);
		
		_attackTask = ThreadPool.schedule(() -> onHitTimer(), sAtk / 2);
		
		attack.hit(attack.createHit(target, damage1, miss1, crit1, shld1), attack.createHit(target, damage2, miss2, crit2, shld2));
		
		return (!miss1 || !miss2);
	}
	
	/**
	 * Launch a Pole attack.<BR>
	 * <B><U> Actions</U> :</B>
	 * <ul>
	 * <li>Get all visible objects in a spherical area near the Creature to obtain possible targets</li>
	 * <li>If possible target is the Creature targeted, launch a simple attack against it</li>
	 * <li>If possible target isn't the Creature targeted but is attackable, launch a simple attack against it</li>
	 * </ul>
	 * @param attack Server->Client packet Attack in which the hit will be added
	 * @param target The Creature targeted
	 * @param sAtk The Attack Speed of the attacker
	 * @return True if one hit isn't missed
	 */
	private boolean doAttackHitByPole(Attack attack, Creature target, int sAtk)
	{
		final int maxRadius = _creature.getPhysicalAttackRange();
		final int maxAngleDiff = (int) _creature.getStat().calcStat(Stats.POWER_ATTACK_ANGLE, 120, null, null);
		final boolean canHitPlayable = target instanceof Playable;
		// Get the number of targets (-1 because the main target is already used)
		final int attackRandomCountMax = (int) _creature.getStat().calcStat(Stats.ATTACK_COUNT_MAX, 0, null, null) - 1;
		final ArrayList<HitHolder> hitHolders = new ArrayList<>();
		final HitHolder firstAttack = getHitHolder(attack, target);
		
		hitHolders.add(firstAttack);
		
		boolean hitted = firstAttack._miss;
		int attackcount = 0;
		
		for (final Creature obj : _creature.getKnownTypeInRadius(Creature.class, maxRadius))
		{
			if (obj == target)
				continue;
			
			if (!_creature.isFacing(obj, maxAngleDiff))
				continue;
			
			if (_creature instanceof Playable && obj.isAttackableBy(_creature) && obj.isAttackableWithoutForceBy((Playable) _creature))
			{
				if (obj instanceof Playable && (obj.isInsideZone(ZoneId.PEACE) || !canHitPlayable))
					continue;
				
				attackcount++;
				if (attackcount > attackRandomCountMax)
					break;
				
				final HitHolder nextAttack = getHitHolder(attack, obj);
				hitHolders.add(nextAttack);
				
				hitted |= nextAttack._miss;
			}
			
			if (_creature instanceof Attackable && obj.isAttackableBy(_creature))
			{
				attackcount++;
				if (attackcount > attackRandomCountMax)
					break;
				
				final HitHolder nextAttack = getHitHolder(attack, obj);
				hitHolders.add(nextAttack);
				
				hitted |= nextAttack._miss;
			}
		}
		
		setAttackTask(hitHolders.toArray(new HitHolder[] {}), WeaponType.POLE, sAtk);
		_attackTask = ThreadPool.schedule(() -> onHitTimer(), sAtk);
		
		for (HitHolder hitHolder : hitHolders)
			attack.hit(attack.createHit(hitHolder._target, hitHolder._damage, hitHolder._miss, hitHolder._crit, hitHolder._shld));
		
		return hitted;
	}
	
	/**
	 * Launch a simple attack.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B>
	 * <ul>
	 * <li>Calculate if hit is missed or not</li>
	 * <li>If hit isn't missed, calculate if shield defense is efficient</li>
	 * <li>If hit isn't missed, calculate if hit is critical</li>
	 * <li>If hit isn't missed, calculate physical damages</li>
	 * <li>Create a new hit task with Medium priority</li>
	 * <li>Add this hit to the Server-Client packet Attack</li>
	 * </ul>
	 * @param attack Server->Client packet Attack in which the hit will be added
	 * @param target The Creature targeted
	 * @param sAtk The Attack Speed of the attacker
	 * @return True if the hit isn't missed
	 */
	private boolean doAttackHitSimple(Attack attack, Creature target, int sAtk)
	{
		int damage1 = 0;
		byte shld1 = 0;
		boolean crit1 = false;
		final boolean miss1 = Formulas.calcHitMiss(_creature, target);
		if (!miss1)
		{
			shld1 = Formulas.calcShldUse(_creature, target, null);
			crit1 = Formulas.calcCrit(_creature.getStat().getCriticalHit(target, null));
			damage1 = (int) Formulas.calcPhysDam(_creature, target, null, shld1, crit1, attack.soulshot);
		}
		
		setAttackTask(new HitHolder[]
		{
			new HitHolder(target, damage1, crit1, miss1, shld1)
		}, WeaponType.ETC, sAtk);
		
		_attackTask = ThreadPool.schedule(() -> onHitTimer(), sAtk);
		
		attack.hit(attack.createHit(target, damage1, miss1, crit1, shld1));
		
		return !miss1;
	}
	
	private HitHolder getHitHolder(Attack attack, Creature target)
	{
		int damage1 = 0;
		byte shld1 = 0;
		boolean crit1 = false;
		
		final boolean miss1 = Formulas.calcHitMiss(_creature, target);
		if (!miss1)
		{
			shld1 = Formulas.calcShldUse(_creature, target, null);
			crit1 = Formulas.calcCrit(_creature.getStat().getCriticalHit(target, null));
			damage1 = (int) Formulas.calcPhysDam(_creature, target, null, shld1, crit1, attack.soulshot);
		}
		
		return new HitHolder(target, damage1, crit1, miss1, shld1);
	}
	
	/**
	 * Abort the current attack of the {@link Creature} and send {@link ActionFailed} packet.
	 */
	public final void stop()
	{
		if (_isBow)
			_isBowCoolingDown = false;
		
		clearAttackTask();
		
		if (_attackTask != null)
		{
			_attackTask.cancel(false);
			_attackTask = null;
		}
		
		_creature.getAI().tryTo(IntentionType.ACTIVE, null, null);
		_creature.getAI().clientActionFailed();
	}
	
	/**
	 * Abort the current attack and send {@link SystemMessageId#ATTACK_FAILED} to the {@link Creature}.
	 */
	public void interrupt()
	{
		if (_isAttackingNow)
		{
			stop();
			_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.ATTACK_FAILED));
		}
	}
	
	private void setAttackTask(HitHolder[] hitHolders, WeaponType weaponType, int afterAttackDelay)
	{
		_hitHolders = hitHolders;
		_weaponType = weaponType;
		_afterAttackDelay = afterAttackDelay;
		_isAttackingNow = true;
		_isBow = (weaponType == WeaponType.BOW);
		_isBowCoolingDown = _isBow;
	}
	
	private void clearAttackTask()
	{
		_hitHolders = null;
		_weaponType = null;
		_afterAttackDelay = 0;
		_isAttackingNow = false;
		_isBow = false;
	}
	
	class HitHolder
	{
		Creature _target;
		int _damage;
		boolean _crit;
		boolean _miss;
		byte _shld;
		
		public HitHolder(Creature target, int damage, boolean crit, boolean miss, byte shld)
		{
			_target = target;
			_damage = damage;
			_crit = crit;
			_shld = shld;
			_miss = miss;
		}
	}
}