package net.sf.l2j.gameserver.model.actor.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import net.sf.l2j.commons.concurrent.ThreadPool;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.data.SkillTable;
import net.sf.l2j.gameserver.data.manager.DuelManager;
import net.sf.l2j.gameserver.enums.AiEventType;
import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.enums.items.ShotType;
import net.sf.l2j.gameserver.enums.skills.SkillType;
import net.sf.l2j.gameserver.handler.ISkillHandler;
import net.sf.l2j.gameserver.handler.SkillHandler;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.entity.Duel;
import net.sf.l2j.gameserver.model.group.Party;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.skills.AbstractEffect;
import net.sf.l2j.gameserver.skills.Formulas;
import net.sf.l2j.gameserver.skills.L2Skill;
import net.sf.l2j.gameserver.skills.l2skills.L2SkillDrain;
import net.sf.l2j.gameserver.taskmanager.AttackStanceTaskManager;

public class Cubic
{
	// Type of cubics
	public static final int STORM_CUBIC = 1;
	public static final int VAMPIRIC_CUBIC = 2;
	public static final int LIFE_CUBIC = 3;
	public static final int VIPER_CUBIC = 4;
	public static final int POLTERGEIST_CUBIC = 5;
	public static final int BINDING_CUBIC = 6;
	public static final int AQUA_CUBIC = 7;
	public static final int SPARK_CUBIC = 8;
	public static final int ATTRACT_CUBIC = 9;
	
	// Max range of cubic skills
	public static final int MAX_MAGIC_RANGE = 900;
	
	// Cubic skills
	public static final int SKILL_CUBIC_HEAL = 4051;
	public static final int SKILL_CUBIC_CURE = 5579;
	
	private Player _owner;
	
	private int _id;
	private int _matk;
	private int _activationTime;
	private int _activationChance;
	private final boolean _givenByOther;
	
	private final List<L2Skill> _skills = new ArrayList<>(3);
	
	private Future<?> _actionTask;
	private Future<?> _disappearTask;
	
	public Cubic(Player owner, int id, int level, int mAtk, int activationTime, int activationChance, int totalLifeTime, boolean givenByOther)
	{
		_owner = owner;
		_id = id;
		_matk = mAtk;
		_activationTime = activationTime * 1000;
		_activationChance = activationChance;
		_givenByOther = givenByOther;
		
		switch (_id)
		{
			case STORM_CUBIC:
				_skills.add(SkillTable.getInstance().getInfo(4049, level));
				break;
			
			case VAMPIRIC_CUBIC:
				_skills.add(SkillTable.getInstance().getInfo(4050, level));
				break;
			
			case LIFE_CUBIC:
				_skills.add(SkillTable.getInstance().getInfo(4051, level));
				doAction();
				break;
			
			case VIPER_CUBIC:
				_skills.add(SkillTable.getInstance().getInfo(4052, level));
				break;
			
			case POLTERGEIST_CUBIC:
				_skills.add(SkillTable.getInstance().getInfo(4053, level));
				_skills.add(SkillTable.getInstance().getInfo(4054, level));
				_skills.add(SkillTable.getInstance().getInfo(4055, level));
				break;
			
			case BINDING_CUBIC:
				_skills.add(SkillTable.getInstance().getInfo(4164, level));
				break;
			
			case AQUA_CUBIC:
				_skills.add(SkillTable.getInstance().getInfo(4165, level));
				break;
			
			case SPARK_CUBIC:
				_skills.add(SkillTable.getInstance().getInfo(4166, level));
				break;
			
			case ATTRACT_CUBIC:
				_skills.add(SkillTable.getInstance().getInfo(5115, level));
				_skills.add(SkillTable.getInstance().getInfo(5116, level));
				break;
		}
		
		_disappearTask = ThreadPool.schedule(this::stop, totalLifeTime);
	}
	
	public synchronized void doAction()
	{
		if (_actionTask != null)
			return;
		
		_actionTask = ThreadPool.scheduleAtFixedRate(this::fireAction, _activationTime, _activationTime);
	}
	
	public int getId()
	{
		return _id;
	}
	
	public Player getOwner()
	{
		return _owner;
	}
	
	public final int getMCriticalHit(Creature target, L2Skill skill)
	{
		return _owner.getMCriticalHit(target, skill);
	}
	
	public int getMAtk()
	{
		return _matk;
	}
	
	public boolean givenByOther()
	{
		return _givenByOther;
	}
	
	public void stopAction()
	{
		if (_actionTask != null)
		{
			_actionTask.cancel(false);
			_actionTask = null;
		}
	}
	
	public void cancelDisappear()
	{
		if (_disappearTask != null)
		{
			_disappearTask.cancel(false);
			_disappearTask = null;
		}
	}
	
	/**
	 * Refresh disappear task timer, with total lifetime set as parameter.
	 * @param totalLifeTime : The total lifetime used as new disappear task timer.
	 */
	public void refreshDisappearTask(int totalLifeTime)
	{
		// Cancel current disappearTask.
		cancelDisappear();
		
		// Restart a task.
		_disappearTask = ThreadPool.schedule(this::stop, totalLifeTime);
	}
	
	/**
	 * @return a valid enemy {@link Creature} target for an offensive cubic, or null otherwise.
	 */
	private Creature pickEnemyTarget()
	{
		final WorldObject ownerTarget = _owner.getTarget();
		if (ownerTarget == null || !_owner.isIn3DRadius(ownerTarget, MAX_MAGIC_RANGE))
			return null;
		
		Creature target = null;
		
		if (_owner.isInDuel())
		{
			final Duel duel = DuelManager.getInstance().getDuel(_owner.getDuelId());
			final Player playerA = duel.getPlayerA();
			final Player playerB = duel.getPlayerB();
			
			if (duel.isPartyDuel())
			{
				final Party partyA = playerA.getParty();
				final Party partyB = playerB.getParty();
				if (partyA == null || partyB == null)
					return null;
				
				final Party partyEnemy = (partyA.containsPlayer(_owner)) ? partyB : partyA;
				if (partyEnemy.containsPlayer(ownerTarget))
					target = (Creature) ownerTarget;
			}
			else
			{
				if (playerA != _owner && ownerTarget == playerA)
					target = playerA;
				else if (playerB != _owner && ownerTarget == playerB)
					target = playerB;
			}
		}
		else if (_owner.isInOlympiadMode())
		{
			if (_owner.isOlympiadStart() && ownerTarget instanceof Playable)
			{
				final Player targetPlayer = ownerTarget.getActingPlayer();
				if (targetPlayer != null && targetPlayer.getOlympiadGameId() == _owner.getOlympiadGameId() && targetPlayer.getOlympiadSide() != _owner.getOlympiadSide())
					target = (Creature) ownerTarget;
			}
		}
		else if (ownerTarget instanceof Creature && ownerTarget != _owner.getSummon() && ownerTarget != _owner)
		{
			if (ownerTarget instanceof Attackable && !((Attackable) ownerTarget).isDead())
			{
				if (((Attackable) ownerTarget).getAggroList().get(_owner) != null)
					target = (Creature) ownerTarget;
				else if (_owner.getSummon() != null && ((Attackable) ownerTarget).getAggroList().get(_owner.getSummon()) != null)
					target = (Creature) ownerTarget;
			}
			else if (ownerTarget instanceof Playable && ((_owner.getPvpFlag() > 0 && !_owner.isInsideZone(ZoneId.PEACE)) || _owner.isInsideZone(ZoneId.PVP)))
			{
				Player enemy = null;
				if (!((Creature) ownerTarget).isDead())
					enemy = ownerTarget.getActingPlayer();
				
				if (enemy != null)
				{
					final Party ownerParty = _owner.getParty();
					if (ownerParty != null)
					{
						if (ownerParty.containsPlayer(enemy))
							return null;
						
						if (ownerParty.getCommandChannel() != null && ownerParty.getCommandChannel().containsPlayer(enemy))
							return null;
					}
					
					if (_owner.getClan() != null && !_owner.isInsideZone(ZoneId.PVP))
					{
						if (_owner.getClan().isMember(enemy.getObjectId()))
							return null;
						
						if (_owner.getAllyId() > 0 && enemy.getAllyId() > 0 && _owner.getAllyId() == enemy.getAllyId())
							return null;
					}
					
					if (enemy.getPvpFlag() == 0 && !enemy.isInsideZone(ZoneId.PVP))
						return null;
					
					if (enemy.isInsideZone(ZoneId.PEACE))
						return null;
					
					if (_owner.getSiegeState() > 0 && _owner.getSiegeState() == enemy.getSiegeState())
						return null;
					
					if (!enemy.isVisible())
						return null;
					
					target = enemy;
				}
			}
		}
		return target;
	}
	
	/**
	 * @return a valid friendly {@link Creature} target for the healing cubic, or null otherwise.
	 */
	private Creature pickFriendlyTarget()
	{
		Creature target = null;
		double ratio = 1.;
		
		Party party = _owner.getParty();
		if (_owner.isInOlympiadMode() || (_owner.isInDuel() && !DuelManager.getInstance().getDuel(_owner.getDuelId()).isPartyDuel()))
			party = null;
		
		if (party != null)
		{
			for (Player member : party.getMembers())
			{
				final double testedRatio = member.getCurrentHp() / member.getMaxHp();
				if (!member.isDead() && testedRatio < 1. && ratio > testedRatio && _owner.isIn3DRadius(member, MAX_MAGIC_RANGE))
				{
					target = member;
					ratio = testedRatio;
				}
			}
		}
		else
		{
			final double testedRatio = _owner.getCurrentHp() / _owner.getMaxHp();
			if (testedRatio < 1.)
			{
				target = _owner;
				ratio = testedRatio;
			}
		}
		
		// Once a valid target is chosen, we test the probability to heal it based on left HP ratio.
		if (target != null)
		{
			final int i0 = Rnd.get(100);
			if (ratio > 0.6)
			{
				if (i0 > 13)
					return null;
			}
			else if (ratio < 0.3)
			{
				if (i0 > 53)
					return null;
			}
			else if (i0 > 33)
				return null;
		}
		return target;
	}
	
	/**
	 * Stop entirely this {@link Cubic} action (both action/disappear tasks are dropped, id is removed from {@link Player} owner. Enforce broadcast.
	 * @see #stop(boolean)
	 */
	public void stop()
	{
		stop(true);
	}
	
	/**
	 * Stop entirely this {@link Cubic} action (both action/disappear tasks are dropped, id is removed from {@link Player} owner.
	 * @param doBroadcast : If true, we broadcast UserInfo/CharInfo.
	 */
	public void stop(boolean doBroadcast)
	{
		stopAction();
		cancelDisappear();
		
		_owner.removeCubic(_id);
		
		if (doBroadcast)
			_owner.broadcastUserInfo();
	}
	
	/**
	 * Fire the action associated to this {@link Cubic} id. If the owner is dead or offline, stop the {@link Cubic} entirely.
	 */
	private void fireAction()
	{
		if (_owner.isDead() || !_owner.isOnline())
		{
			stop();
			return;
		}
		
		if (_id == LIFE_CUBIC)
		{
			final L2Skill skill = _skills.stream().filter(s -> s.getId() == SKILL_CUBIC_HEAL).findFirst().orElse(null);
			if (skill == null)
				return;
			
			final Creature target = pickFriendlyTarget();
			if (target == null)
				return;
			
			final Creature[] targets =
			{
				target
			};
			
			final ISkillHandler handler = SkillHandler.getInstance().getHandler(skill.getSkillType());
			if (handler != null)
				handler.useSkill(_owner, skill, targets);
			else
				skill.useSkill(_owner, targets);
			
			_owner.broadcastPacket(new MagicSkillUse(_owner, target, skill.getId(), skill.getLevel(), 0, 0));
		}
		else
		{
			if (!AttackStanceTaskManager.getInstance().isInAttackStance(_owner))
			{
				stopAction();
				return;
			}
			
			if (Rnd.get(100) >= _activationChance)
				return;
			
			final L2Skill skill = Rnd.get(_skills);
			if (skill == null)
				return;
			
			final Creature target = pickEnemyTarget();
			if (target == null)
				return;
			
			final Creature[] targets =
			{
				target
			};
			
			switch (skill.getSkillType())
			{
				case PARALYZE:
				case STUN:
				case ROOT:
				case AGGDAMAGE:
					useCubicDisabler(skill.getSkillType(), skill, targets);
					break;
				
				case MDAM:
					useCubicMdam(skill, targets);
					break;
				
				case POISON:
				case DEBUFF:
				case DOT:
					useCubicContinuous(skill, targets);
					break;
				
				case DRAIN:
					((L2SkillDrain) skill).useCubicSkill(this, targets);
					break;
				
				default:
					SkillHandler.getInstance().getHandler(skill.getSkillType()).useSkill(_owner, skill, targets);
					break;
			}
			_owner.broadcastPacket(new MagicSkillUse(_owner, target, skill.getId(), skill.getLevel(), 0, 0));
		}
	}
	
	public void useCubicContinuous(L2Skill skill, WorldObject[] targets)
	{
		final boolean bss = getOwner().isChargedShot(ShotType.BLESSED_SPIRITSHOT);
		
		for (WorldObject obj : targets)
		{
			if (!(obj instanceof Creature))
				continue;
			
			final Creature target = ((Creature) obj);
			if (target.isDead())
				continue;
			
			if (skill.isOffensive())
			{
				final byte shld = Formulas.calcShldUse(getOwner(), target, skill);
				final boolean acted = Formulas.calcCubicSkillSuccess(this, target, skill, shld, bss);
				
				if (!acted)
				{
					getOwner().sendPacket(SystemMessageId.ATTACK_FAILED);
					continue;
				}
			}
			
			// If this is a debuff, let the duel manager know about it so the debuff can be removed after the duel (player & target must be in the same duel)
			if (target instanceof Player && ((Player) target).isInDuel() && skill.getSkillType() == SkillType.DEBUFF && getOwner().getDuelId() == ((Player) target).getDuelId())
			{
				for (AbstractEffect effect : skill.getEffects(getOwner(), target))
					DuelManager.getInstance().onBuff(((Player) target), effect);
			}
			else
				skill.getEffects(this, target);
		}
	}
	
	public void useCubicMdam(L2Skill skill, WorldObject[] targets)
	{
		final boolean bss = getOwner().isChargedShot(ShotType.BLESSED_SPIRITSHOT);
		
		for (WorldObject obj : targets)
		{
			if (!(obj instanceof Creature))
				continue;
			
			final Creature target = ((Creature) obj);
			if (target.isDead())
				continue;
			
			final boolean mcrit = Formulas.calcMCrit(getMCriticalHit(target, skill));
			final byte shld = Formulas.calcShldUse(getOwner(), target, skill);
			
			int damage = (int) Formulas.calcMagicDam(this, target, skill, mcrit, shld);
			
			// If target is reflecting the skill then no damage is done Ignoring vengance-like reflections
			if ((Formulas.calcSkillReflect(target, skill) & Formulas.SKILL_REFLECT_SUCCEED) > 0)
				damage = 0;
			
			if (damage > 0)
			{
				// Manage cast break of the target (calculating rate, sending message...)
				Formulas.calcCastBreak(target, damage);
				
				getOwner().sendDamageMessage(target, damage, mcrit, false, false);
				
				if (skill.hasEffects())
				{
					// activate attacked effects, if any
					target.stopSkillEffects(skill.getId());
					
					if (target.getFirstEffect(skill) != null)
						target.removeEffect(target.getFirstEffect(skill));
					
					if (Formulas.calcCubicSkillSuccess(this, target, skill, shld, bss))
						skill.getEffects(this, target);
				}
				
				target.reduceCurrentHp(damage, getOwner(), skill);
			}
		}
	}
	
	public void useCubicDisabler(SkillType type, L2Skill skill, WorldObject[] targets)
	{
		final boolean bss = getOwner().isChargedShot(ShotType.BLESSED_SPIRITSHOT);
		
		for (WorldObject obj : targets)
		{
			if (!(obj instanceof Creature))
				continue;
			
			final Creature target = ((Creature) obj);
			if (target.isDead())
				continue;
			
			final byte shld = Formulas.calcShldUse(getOwner(), target, skill);
			
			switch (type)
			{
				case STUN:
				case PARALYZE:
				case ROOT:
					if (Formulas.calcCubicSkillSuccess(this, target, skill, shld, bss))
					{
						// If this is a debuff, let the duel manager know about it so the debuff can be removed after the duel (player & target must be in the same duel)
						if (target instanceof Player && ((Player) target).isInDuel() && skill.getSkillType() == SkillType.DEBUFF && getOwner().getDuelId() == ((Player) target).getDuelId())
						{
							for (AbstractEffect effect : skill.getEffects(getOwner(), target))
								DuelManager.getInstance().onBuff(((Player) target), effect);
						}
						else
							skill.getEffects(this, target);
					}
					break;
				
				case AGGDAMAGE:
					if (Formulas.calcCubicSkillSuccess(this, target, skill, shld, bss))
					{
						if (target instanceof Attackable)
							target.getAI().notifyEvent(AiEventType.AGGRESSION, getOwner(), (int) ((150 * skill.getPower()) / (target.getLevel() + 7)));
						
						skill.getEffects(this, target);
					}
					break;
			}
		}
	}
}