package net.sf.l2j.gameserver.model.actor.ai.type;

import java.util.List;

import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.commons.util.ArraysUtil;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.enums.SiegeSide;
import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.enums.actors.NpcAiType;
import net.sf.l2j.gameserver.enums.actors.NpcSkillType;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.SiegeGuard;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.skills.L2Skill;

public class SiegeGuardAI extends AttackableAI
{
	public SiegeGuardAI(SiegeGuard guard)
	{
		super(guard);
	}
	
	/**
	 * Following conditions are checked for a siege defender :
	 * <ul>
	 * <li>if target isn't a player or a summon.</li>
	 * <li>if target is dead.</li>
	 * <li>if target is a GM in hide mode.</li>
	 * <li>if player is silent moving.</li>
	 * <li>if the target can't be seen and is a defender.</li>
	 * </ul>
	 * @param target The targeted Creature.
	 * @return True if the target is autoattackable (depends on the actor type).
	 */
	@Override
	protected boolean autoAttackCondition(Creature target)
	{
		if (!(target instanceof Playable) || target.isAlikeDead())
			return false;
		
		final Player player = target.getActingPlayer();
		if (player == null)
			return false;
		
		// Check if the target isn't GM on hide mode.
		if (player.isGM() && !player.getAppearance().isVisible())
			return false;
		
		// Check if the target isn't in silent move mode AND too far
		if (player.isSilentMoving() && !_actor.isIn3DRadius(player, 250))
			return false;
		
		return (target.isAttackableBy(_actor) && GeoEngine.getInstance().canSeeTarget(_actor, target));
	}
	
	/**
	 * Manage AI when not engaged in combat.
	 * <ul>
	 * <li>Update every 1s the _globalAggro counter to come close to 0</li>
	 * <li>If the actor is Aggressive and can attack, add all autoAttackable Creature in its Aggro Range to its _aggroList, chose a target and order to attack it</li>
	 * <li>If the actor can't attack, order to it to return to its home location</li>
	 * </ul>
	 */
	@Override
	protected void peaceMode()
	{
		// Update every 1s the _globalAggro counter to come close to 0
		if (_globalAggro != 0)
		{
			if (_globalAggro < 0)
				_globalAggro++;
			else
				_globalAggro--;
		}
		
		// Add all autoAttackable Creature in L2Attackable Aggro Range to its _aggroList with 0 damage and 1 hate
		// A L2Attackable isn't aggressive during 10s after its spawn because _globalAggro is set to -10
		if (_globalAggro >= 0)
		{
			final Attackable npc = (Attackable) _actor;
			for (Creature obj : npc.getKnownTypeInRadius(Creature.class, npc.getTemplate().getClanRange()))
			{
				if (autoAttackCondition(obj)) // check aggression
				{
					// Get the hate level of the L2Attackable against this target, and add the attacker to the L2Attackable _aggroList
					if (npc.getHating(obj) == 0)
						npc.addDamageHate(obj, 0, 1);
				}
			}
			
			// Chose a target from its aggroList
			final Creature target = (_actor.isConfused()) ? getCurrentIntention().getFinalTarget() : npc.getMostHated();
			if (target != null)
			{
				// Get the hate level of the L2Attackable against this Creature target contained in _aggroList
				if (npc.getHating(target) + _globalAggro > 0)
				{
					// Set the Creature movement type to run and send Server->Client packet ChangeMoveType to all others Player
					_actor.forceRunStance();
					
					// Set the AI Intention to ATTACK
					tryToAttack(target);
				}
				return;
			}
		}
		// Order to the SiegeGuard to return to its home location because there's no target to attack
		getActor().returnHome();
	}
	
	/**
	 * Manage AI when engaged in combat.
	 * <ul>
	 * <li>Update the attack timeout if actor is running</li>
	 * <li>If target is dead or timeout is expired, stop this attack and set the Intention to ACTIVE</li>
	 * <li>Call all WorldObject of its Faction inside the Faction Range</li>
	 * <li>Chose a target and order to attack it with magic skill or physical attack</li>
	 * </ul>
	 */
	@Override
	protected void combatMode()
	{
		final SiegeGuard actor = getActor();
		
		/**
		 * RETURN HOME<br>
		 * Check if the siege guard isn't too far ; if yes, then move him back to home.
		 */
		if (!actor.isInsideZone(ZoneId.SIEGE))
		{
			actor.returnHome();
			return;
		}
		
		// Pickup attack target.
		Creature target = actor.getMostHated();
		
		// If target doesn't exist, is too far or if timeout is expired.
		if (target == null || _attackTimeout < System.currentTimeMillis() || !actor.isIn3DRadius(target, 2000))
		{
			// Stop hating this target after the attack timeout or if target is dead
			actor.stopHating(target);
			
			// Search the nearest target. If a target is found, continue regular process, else drop angry behavior.
			target = targetReconsider(actor.getTemplate().getClanRange(), false);
			if (target == null)
			{
				doActiveIntention();
				return;
			}
		}
		
		/**
		 * COMMON INFORMATIONS<br>
		 * Used for range and distance check.
		 */
		
		final int actorCollision = (int) actor.getCollisionRadius();
		final int combinedCollision = (int) (actorCollision + target.getCollisionRadius());
		final double dist = actor.distance2D(target);
		
		int range = combinedCollision;
		if (target.isMoving())
			range += 15;
		
		if (actor.isMoving())
			range += 15;
		
		/**
		 * Cast a spell.
		 */
		
		if (willCastASpell())
		{
			// This list is used in order to avoid multiple calls on skills lists. Tests are made one after the other, and content is replaced when needed.
			List<L2Skill> defaultList;
			
			// -------------------------------------------------------------------------------
			// Heal
			defaultList = actor.getTemplate().getSkills(NpcSkillType.HEAL);
			if (!defaultList.isEmpty())
			{
				final String[] clans = actor.getTemplate().getClans();
				
				// Go through all characters around the actor that belongs to its faction.
				for (Creature cha : actor.getKnownTypeInRadius(Creature.class, 1000))
				{
					// Don't bother about dead, not visible, or healthy characters.
					if (cha.isAlikeDead() || !GeoEngine.getInstance().canSeeTarget(actor, cha) || cha.getStatus().getHpRatio() > 0.75)
						continue;
					
					// Will affect only defenders or NPCs from same faction.
					if (!actor.isAttackingDisabled() && (cha instanceof Player && actor.getCastle().getSiege().checkSides(((Player) cha).getClan(), SiegeSide.DEFENDER, SiegeSide.OWNER)) || (cha instanceof Npc && ArraysUtil.contains(clans, ((Npc) cha).getTemplate().getClans())))
					{
						for (L2Skill sk : defaultList)
						{
							useMagic(sk, cha, dist, range + sk.getSkillRadius());
							return;
						}
					}
				}
			}
			
			// -------------------------------------------------------------------------------
			// Buff
			defaultList = actor.getTemplate().getSkills(NpcSkillType.BUFF);
			if (!defaultList.isEmpty())
			{
				for (L2Skill sk : defaultList)
				{
					if (actor.getFirstEffect(sk) == null)
					{
						useMagic(sk, actor, dist, range + sk.getSkillRadius());
						return;
					}
				}
			}
			
			// -------------------------------------------------------------------------------
			// Debuff - 10% luck to get debuffed.
			defaultList = actor.getTemplate().getSkills(NpcSkillType.DEBUFF);
			if (Rnd.get(100) < 10 && !defaultList.isEmpty())
			{
				for (L2Skill sk : defaultList)
				{
					if (target.getFirstEffect(sk) == null)
					{
						useMagic(sk, target, dist, range + sk.getSkillRadius());
						return;
					}
				}
			}
			
			// -------------------------------------------------------------------------------
			// General attack skill - short range is checked, then long range.
			defaultList = actor.getTemplate().getSkills(NpcSkillType.SHORT_RANGE);
			if (!defaultList.isEmpty() && dist <= 150)
			{
				final L2Skill skill = Rnd.get(defaultList);
				if (useMagic(skill, target, dist, skill.getCastRange()))
					return;
			}
			else
			{
				defaultList = actor.getTemplate().getSkills(NpcSkillType.LONG_RANGE);
				if (!defaultList.isEmpty() && dist > 150)
				{
					final L2Skill skill = Rnd.get(defaultList);
					if (useMagic(skill, target, dist, skill.getCastRange()))
						return;
				}
			}
		}
		
		/**
		 * MELEE CHECK<br>
		 * The mob failed a skill check ; make him flee if AI authorizes it, else melee attack.
		 */
		
		// The range takes now in consideration physical attack range.
		range += actor.getStatus().getPhysicalAttackRange();
		
		if (actor.isMovementDisabled())
		{
			// If distance is too big, choose another target.
			if (dist > range)
				target = targetReconsider(range, true);
			
			// Any AI type, even healer or mage, will try to melee attack if it can't do anything else (desesperate situation).
			if (target != null)
				tryToAttack(target);
			
			return;
		}
		
		/**
		 * MOVE AROUND CHECK<br>
		 * In case many mobs are trying to hit from same place, move a bit, circling around the target
		 */
		
		if (Rnd.get(100) <= 3)
		{
			for (Attackable nearby : actor.getKnownTypeInRadius(Attackable.class, actorCollision))
			{
				if (nearby == target)
					continue;
				
				int newX = combinedCollision + Rnd.get(40);
				if (Rnd.nextBoolean())
					newX = target.getX() + newX;
				else
					newX = target.getX() - newX;
				
				int newY = combinedCollision + Rnd.get(40);
				if (Rnd.nextBoolean())
					newY = target.getY() + newY;
				else
					newY = target.getY() - newY;
				
				if (!actor.isIn2DRadius(newX, newY, actorCollision))
					actor.getMove().maybeMoveToLocation(new Location(newX, newY, actor.getZ()), 0, true, false);
				
				return;
			}
		}
		
		/**
		 * FLEE CHECK<br>
		 * Test the flee possibility. Archers got 25% chance to flee.
		 */
		
		if (actor.getTemplate().getAiType() == NpcAiType.ARCHER && dist <= (60 + combinedCollision) && Rnd.get(4) < 1)
		{
			getActor().fleeFrom(target, Config.MAX_DRIFT_RANGE);
			return;
		}
		
		/**
		 * BASIC MELEE ATTACK
		 */
		
		tryToAttack(target);
	}
	
	/**
	 * Method used when the actor can't attack his current target (immobilize state, for exemple).
	 * <ul>
	 * <li>If the actor got an hate list, pickup a new target from it.</li>
	 * <li>If the selected target is a defenser, drop from the list and pickup another.</li>
	 * </ul>
	 * @param range The range to check (skill range for skill ; physical range for melee).
	 * @param rangeCheck That boolean is used to see if a check based on the distance must be made (skill check).
	 * @return The new Creature victim.
	 */
	@Override
	protected Creature targetReconsider(int range, boolean rangeCheck)
	{
		final Attackable actor = getActor();
		
		// Verify first if aggro list is empty, if not search a victim following his aggro position.
		if (!actor.getAggroList().isEmpty())
		{
			// Store aggro value && most hated, in order to add it to the random target we will choose.
			final Creature previousMostHated = actor.getMostHated();
			final int aggroMostHated = actor.getHating(previousMostHated);
			
			for (Creature obj : actor.getHateList())
			{
				if (!autoAttackCondition(obj))
					continue;
				
				if (rangeCheck)
				{
					// Verify the distance, -15 if the victim is moving, -15 if the npc is moving.
					double dist = actor.distance2D(obj) - obj.getCollisionRadius();
					if (actor.isMoving())
						dist -= 15;
					
					if (obj.isMoving())
						dist -= 15;
					
					if (dist > range)
						continue;
				}
				
				// Stop to hate the most hated.
				actor.stopHating(previousMostHated);
				
				// Add previous most hated aggro to that new victim.
				actor.addDamageHate(obj, 0, (aggroMostHated > 0) ? aggroMostHated : 2000);
				return obj;
			}
		}
		return null;
	}
	
	@Override
	public SiegeGuard getActor()
	{
		return (SiegeGuard) _actor;
	}
}