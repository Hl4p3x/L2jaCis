package net.sf.l2j.gameserver.model.actor.attack;

import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.SkillTable.FrequentSkill;
import net.sf.l2j.gameserver.enums.AiEventType;
import net.sf.l2j.gameserver.enums.GaugeColor;
import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.enums.items.ShotType;
import net.sf.l2j.gameserver.enums.items.WeaponType;
import net.sf.l2j.gameserver.enums.skills.Stats;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Creature;
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
import net.sf.l2j.gameserver.skills.Formulas;
import net.sf.l2j.gameserver.skills.L2Skill;

/**
 * This class groups all attack data related to a {@link Creature}.
 * @param <T> : The {@link Creature} used as actor.
 */
public class CreatureAttack<T extends Creature>
{
	public static final CLogger LOGGER = new CLogger(CreatureAttack.class.getName());
	
	protected final T _actor;
	
	protected volatile boolean _isAttackingNow;
	protected volatile boolean _isBowCoolingDown;
	
	protected HitHolder[] _hitHolders;
	protected WeaponType _weaponType;
	protected int _afterAttackDelay;
	protected boolean _isBow;
	
	protected ScheduledFuture<?> _attackTask;
	
	public CreatureAttack(T actor)
	{
		_actor = actor;
	}
	
	public boolean isAttackingNow()
	{
		return _isAttackingNow;
	}
	
	public boolean isBowCoolingDown()
	{
		return _isBowCoolingDown;
	}
	
	/**
	 * @param target The target to check
	 * @return True if the attacker doesn't have isAttackingDisabled
	 */
	public boolean canDoAttack(Creature target)
	{
		if (_actor.isAttackingDisabled())
			return false;
		
		if (!target.isAttackableBy(_actor) || !_actor.knows(target))
			return false;
		
		if (!GeoEngine.getInstance().canSeeTarget(_actor, target))
		{
			_actor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANT_SEE_TARGET));
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
		if (mainTarget.isDead() || !_actor.knows(mainTarget))
		{
			stop();
			return;
		}
		
		final Player player = _actor.getActingPlayer();
		if (player != null && player.getSummon() != mainTarget)
			player.updatePvPStatus(mainTarget);
		
		_actor.rechargeShots(true, false);
		
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
				
				_attackTask = ThreadPool.schedule(() ->
				{
					_isBowCoolingDown = false;
					_actor.getAI().notifyEvent(AiEventType.BOW_ATTACK_REUSED, null, null);
					
				}, _afterAttackDelay);
				
				onFinishedAttackBow();
				break;
			
			default:
				doHit(_hitHolders[0]);
				
				_attackTask = ThreadPool.schedule(() -> onFinishedAttack(), _afterAttackDelay);
				break;
		}
	}
	
	private void onFinishedAttackBow()
	{
		clearAttackTask(false);
		
		_actor.getAI().notifyEvent(AiEventType.FINISHED_ATTACK_BOW, null, null);
	}
	
	private void onFinishedAttack()
	{
		clearAttackTask(false);
		
		_actor.getAI().notifyEvent(AiEventType.FINISHED_ATTACK, null, null);
	}
	
	private void doHit(HitHolder hitHolder)
	{
		final Creature target = hitHolder._target;
		if (hitHolder._miss)
		{
			if (target.hasAI())
				target.getAI().notifyEvent(AiEventType.EVADED, _actor, null);
			
			if (target.getChanceSkills() != null)
				target.getChanceSkills().onEvadedHit(_actor);
			
			if (target instanceof Player)
				target.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.AVOIDED_S1_ATTACK).addCharName(_actor));
		}
		
		_actor.sendDamageMessage(target, hitHolder._damage, false, hitHolder._crit, hitHolder._miss);
		
		// Character will be petrified if attacking a raid related object that's more than 8 levels lower
		if (!Config.RAID_DISABLE_CURSE && target.isRaidRelated() && _actor.getStatus().getLevel() > target.getStatus().getLevel() + 8)
		{
			final L2Skill skill = FrequentSkill.RAID_CURSE2.getSkill();
			if (skill != null)
			{
				// Send visual and skill effects. Caster is the victim.
				_actor.broadcastPacket(new MagicSkillUse(_actor, _actor, skill.getId(), skill.getLevel(), 300, 0));
				skill.getEffects(_actor, _actor);
			}
			
			hitHolder._damage = 0; // prevents messing up drop calculation
		}
		
		if (!hitHolder._miss && hitHolder._damage > 0)
		{
			_actor.getAI().startAttackStance();
			
			if (target.hasAI())
				target.getAI().notifyEvent(AiEventType.ATTACKED, _actor, null);
			
			int reflectedDamage = 0;
			
			// Reflect damage system - do not reflect if weapon is a bow or target is invulnerable
			if (!_isBow && !target.isInvul())
			{
				// quick fix for no drop from raid if boss attack high-level char with damage reflection
				if (!target.isRaidRelated() || _actor.getActingPlayer() == null || _actor.getActingPlayer().getStatus().getLevel() <= target.getStatus().getLevel() + 8)
				{
					// Calculate reflection damage to reduce HP of attacker if necessary
					final double reflectPercent = target.getStatus().calcStat(Stats.REFLECT_DAMAGE_PERCENT, 0, null, null);
					if (reflectPercent > 0)
					{
						reflectedDamage = (int) (reflectPercent / 100. * hitHolder._damage);
						
						if (reflectedDamage > target.getStatus().getMaxHp())
							reflectedDamage = target.getStatus().getMaxHp();
					}
				}
			}
			
			// Reduce target HPs
			target.reduceCurrentHp(hitHolder._damage, _actor, null);
			
			// Reduce attacker HPs in case of a reflect.
			if (reflectedDamage > 0)
				_actor.reduceCurrentHp(reflectedDamage, target, true, false, null);
			
			// Calculate the absorbed HP percentage. Do not absorb if weapon is a bow.
			if (!_isBow)
			{
				final double absorbPercent = _actor.getStatus().calcStat(Stats.ABSORB_DAMAGE_PERCENT, 0, null, null);
				if (absorbPercent > 0)
					_actor.getStatus().addHp(absorbPercent / 100. * hitHolder._damage);
			}
			
			// Manage cast break of the target (calculating rate, sending message...)
			Formulas.calcCastBreak(target, hitHolder._damage);
			
			// Maybe launch chance skills on us
			final ChanceSkillList chanceSkills = _actor.getChanceSkills();
			if (chanceSkills != null)
			{
				chanceSkills.onHit(target, false, hitHolder._crit);
				
				// Reflect triggers onHit
				if (reflectedDamage > 0)
					chanceSkills.onHit(target, true, false);
			}
			
			// Maybe launch chance skills on target
			if (target.getChanceSkills() != null)
				target.getChanceSkills().onHit(_actor, true, hitHolder._crit);
			
			// Launch weapon Special ability effect if available
			if (hitHolder._crit)
			{
				final Weapon activeWeapon = _actor.getActiveWeaponItem();
				if (activeWeapon != null)
					activeWeapon.castSkillOnCrit(_actor, target);
			}
		}
	}
	
	/**
	 * Launch a physical attack against a {@link Creature}.
	 * @param target : The {@link Creature} used as target.
	 * @return True if the hit was actually successful, false otherwise.
	 */
	public boolean doAttack(Creature target)
	{
		final int timeAtk = Formulas.calculateTimeBetweenAttacks(_actor);
		final Weapon weaponItem = _actor.getActiveWeaponItem();
		final Attack attack = new Attack(_actor, _actor.isChargedShot(ShotType.SOULSHOT), (weaponItem != null) ? weaponItem.getCrystalType().getId() : 0);
		
		_actor.getPosition().setHeadingTo(target);
		
		boolean isHit;
		
		switch (_actor.getAttackType())
		{
			case BOW:
				isHit = doAttackHitByBow(attack, target, timeAtk, weaponItem);
				break;
			
			case POLE:
				isHit = doAttackHitByPole(attack, target, timeAtk / 2);
				break;
			
			case DUAL:
			case DUALFIST:
				isHit = doAttackHitByDual(attack, target, timeAtk / 2);
				break;
			
			case FIST:
				isHit = (_actor.getSecondaryWeaponItem() instanceof Armor) ? doAttackHitSimple(attack, target, timeAtk / 2) : doAttackHitByDual(attack, target, timeAtk / 2);
				break;
			
			default:
				isHit = doAttackHitSimple(attack, target, timeAtk / 2);
				break;
		}
		
		// Check if hit isn't missed ; if we didn't miss the hit, discharge the shoulshots, if any.
		if (isHit)
			_actor.setChargedShot(ShotType.SOULSHOT, false);
		
		if (attack.hasHits())
			_actor.broadcastPacket(attack);
		
		return isHit;
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
		
		_actor.reduceArrowCount();
		_actor.getStatus().reduceMp(_actor.getActiveWeaponItem().getMpConsume());
		
		final boolean miss1 = Formulas.calcHitMiss(_actor, target);
		if (!miss1)
		{
			shld1 = Formulas.calcShldUse(_actor, target, null);
			crit1 = Formulas.calcCrit(_actor, target, null);
			damage1 = (int) Formulas.calcPhysDam(_actor, target, null, shld1, crit1, attack.soulshot);
		}
		
		int reuse = weapon.getReuseDelay();
		if (reuse != 0)
			reuse = (reuse * 345) / _actor.getStatus().getPAtkSpd();
		
		setAttackTask(new HitHolder[]
		{
			new HitHolder(target, damage1, crit1, miss1, shld1)
		}, WeaponType.BOW, reuse);
		
		_attackTask = ThreadPool.schedule(() -> onHitTimer(), sAtk);
		
		if (_actor instanceof Player)
		{
			_actor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.GETTING_READY_TO_SHOOT_AN_ARROW));
			_actor.sendPacket(new SetupGauge(GaugeColor.RED, sAtk + reuse));
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
		
		final boolean miss1 = Formulas.calcHitMiss(_actor, target);
		if (!miss1)
		{
			shld1 = Formulas.calcShldUse(_actor, target, null);
			crit1 = Formulas.calcCrit(_actor, target, null);
			damage1 = (int) Formulas.calcPhysDam(_actor, target, null, shld1, crit1, attack.soulshot);
			damage1 /= 2;
		}
		
		final boolean miss2 = Formulas.calcHitMiss(_actor, target);
		if (!miss2)
		{
			shld2 = Formulas.calcShldUse(_actor, target, null);
			crit2 = Formulas.calcCrit(_actor, target, null);
			damage2 = (int) Formulas.calcPhysDam(_actor, target, null, shld2, crit2, attack.soulshot);
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
		final int maxRadius = _actor.getStatus().getPhysicalAttackRange();
		final int maxAngleDiff = (int) _actor.getStatus().calcStat(Stats.POWER_ATTACK_ANGLE, 120, null, null);
		final boolean canHitPlayable = target instanceof Playable;
		// Get the number of targets (-1 because the main target is already used)
		final int attackRandomCountMax = (int) _actor.getStatus().calcStat(Stats.ATTACK_COUNT_MAX, 0, null, null) - 1;
		final ArrayList<HitHolder> hitHolders = new ArrayList<>();
		final HitHolder firstAttack = getHitHolder(attack, target);
		
		hitHolders.add(firstAttack);
		
		boolean isHit = firstAttack._miss;
		int attackcount = 0;
		
		for (final Creature obj : _actor.getKnownTypeInRadius(Creature.class, maxRadius))
		{
			if (obj == target)
				continue;
			
			if (!_actor.isFacing(obj, maxAngleDiff))
				continue;
			
			if (_actor instanceof Playable && obj.isAttackableBy(_actor) && obj.isAttackableWithoutForceBy((Playable) _actor))
			{
				if (obj instanceof Playable && (obj.isInsideZone(ZoneId.PEACE) || !canHitPlayable))
					continue;
				
				attackcount++;
				if (attackcount > attackRandomCountMax)
					break;
				
				final HitHolder nextAttack = getHitHolder(attack, obj);
				hitHolders.add(nextAttack);
				
				isHit |= nextAttack._miss;
			}
			
			if (_actor instanceof Attackable && obj.isAttackableBy(_actor))
			{
				attackcount++;
				if (attackcount > attackRandomCountMax)
					break;
				
				final HitHolder nextAttack = getHitHolder(attack, obj);
				hitHolders.add(nextAttack);
				
				isHit |= nextAttack._miss;
			}
		}
		
		setAttackTask(hitHolders.toArray(new HitHolder[] {}), WeaponType.POLE, sAtk);
		_attackTask = ThreadPool.schedule(() -> onHitTimer(), sAtk);
		
		for (HitHolder hitHolder : hitHolders)
			attack.hit(attack.createHit(hitHolder._target, hitHolder._damage, hitHolder._miss, hitHolder._crit, hitHolder._shld));
		
		return isHit;
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
		final boolean miss1 = Formulas.calcHitMiss(_actor, target);
		if (!miss1)
		{
			shld1 = Formulas.calcShldUse(_actor, target, null);
			crit1 = Formulas.calcCrit(_actor, target, null);
			damage1 = (int) Formulas.calcPhysDam(_actor, target, null, shld1, crit1, attack.soulshot);
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
		
		final boolean miss1 = Formulas.calcHitMiss(_actor, target);
		if (!miss1)
		{
			shld1 = Formulas.calcShldUse(_actor, target, null);
			crit1 = Formulas.calcCrit(_actor, target, null);
			damage1 = (int) Formulas.calcPhysDam(_actor, target, null, shld1, crit1, attack.soulshot);
		}
		
		return new HitHolder(target, damage1, crit1, miss1, shld1);
	}
	
	/**
	 * Abort the current attack of the {@link Creature} and send {@link ActionFailed} packet.
	 */
	public final void stop()
	{
		clearAttackTask(true);
		
		if (_attackTask != null)
		{
			_attackTask.cancel(false);
			_attackTask = null;
		}
		
		_actor.getAI().tryToActive();
		_actor.getAI().clientActionFailed();
	}
	
	/**
	 * Abort the current attack and send {@link SystemMessageId#ATTACK_FAILED} to the {@link Creature}.
	 */
	public void interrupt()
	{
		if (_isAttackingNow)
		{
			stop();
			_actor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.ATTACK_FAILED));
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
	
	private void clearAttackTask(boolean clearBowCooldown)
	{
		_hitHolders = null;
		_weaponType = null;
		_afterAttackDelay = 0;
		_isAttackingNow = false;
		_isBow = false;
		
		if (clearBowCooldown)
			_isBowCoolingDown = false;
	}
	
	static class HitHolder
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