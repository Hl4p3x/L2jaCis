package net.sf.l2j.gameserver.model.actor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.manager.CastleManager;
import net.sf.l2j.gameserver.enums.AiEventType;
import net.sf.l2j.gameserver.enums.SiegeSide;
import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.enums.skills.EffectFlag;
import net.sf.l2j.gameserver.enums.skills.EffectType;
import net.sf.l2j.gameserver.model.actor.attack.PlayableAttack;
import net.sf.l2j.gameserver.model.actor.cast.PlayableCast;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.model.actor.instance.SiegeGuard;
import net.sf.l2j.gameserver.model.actor.status.PlayableStatus;
import net.sf.l2j.gameserver.model.actor.template.CreatureTemplate;
import net.sf.l2j.gameserver.model.entity.Duel.DuelState;
import net.sf.l2j.gameserver.model.entity.Siege;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.item.kind.EtcItem;
import net.sf.l2j.gameserver.model.pledge.Clan;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ExUseSharedGroupItem;
import net.sf.l2j.gameserver.network.serverpackets.Revive;
import net.sf.l2j.gameserver.scripting.QuestState;
import net.sf.l2j.gameserver.skills.AbstractEffect;
import net.sf.l2j.gameserver.skills.L2Skill;

/**
 * This class represents all {@link Playable} actors in the world : {@link Player}s and their different {@link Summon} types.
 */
public abstract class Playable extends Creature
{
	private final Map<Integer, Long> _disabledItems = new ConcurrentHashMap<>();
	
	public Playable(int objectId, CreatureTemplate template)
	{
		super(objectId, template);
	}
	
	/**
	 * @return The max weight that the {@link Playable} can carry.
	 */
	public abstract int getWeightLimit();
	
	public abstract int getKarma();
	
	public abstract byte getPvpFlag();
	
	@Override
	public PlayableStatus<? extends Playable> getStatus()
	{
		return (PlayableStatus<?>) _status;
	}
	
	@Override
	public void setStatus()
	{
		_status = new PlayableStatus<>(this);
	}
	
	@Override
	public void setCast()
	{
		_cast = new PlayableCast<>(this);
	}
	
	@Override
	public void setAttack()
	{
		_attack = new PlayableAttack<>(this);
	}
	
	@Override
	public boolean doDie(Creature killer)
	{
		// killing is only possible one time
		synchronized (this)
		{
			if (isDead())
				return false;
			
			// now reset currentHp to zero
			getStatus().setHp(0);
			
			setIsDead(true);
		}
		
		// Stop movement, cast and attack. Reset the target.
		abortAll(true);
		
		// Stop HP/MP/CP Regeneration task
		getStatus().stopHpMpRegeneration();
		
		// Stop all active skills effects in progress
		if (isPhoenixBlessed())
		{
			// remove Lucky Charm if player has SoulOfThePhoenix/Salvation buff
			if (getCharmOfLuck())
				stopCharmOfLuck(null);
			if (isNoblesseBlessed())
				stopNoblesseBlessing(null);
		}
		// Same thing if the Character isn't a Noblesse Blessed L2Playable
		else if (isNoblesseBlessed())
		{
			stopNoblesseBlessing(null);
			
			// remove Lucky Charm if player have Nobless blessing buff
			if (getCharmOfLuck())
				stopCharmOfLuck(null);
		}
		else
			stopAllEffectsExceptThoseThatLastThroughDeath();
		
		// Send the Server->Client packet StatusUpdate with current HP and MP to all other Player to inform
		getStatus().broadcastStatusUpdate();
		
		// Notify Creature AI
		getAI().notifyEvent(AiEventType.DEAD, null, null);
		
		// Notify Quest of L2Playable's death
		final Player actingPlayer = getActingPlayer();
		for (final QuestState qs : actingPlayer.getNotifyQuestOfDeath())
			qs.getQuest().notifyDeath((killer == null ? this : killer), actingPlayer);
		
		if (killer != null)
		{
			final Player player = killer.getActingPlayer();
			if (player != null)
				player.onKillUpdatePvPKarma(this);
		}
		
		return true;
	}
	
	@Override
	public void doRevive()
	{
		if (!isDead() || isTeleporting())
			return;
		
		setIsDead(false);
		
		if (isPhoenixBlessed())
		{
			stopPhoenixBlessing(null);
			
			getStatus().setMaxHpMp();
		}
		else
			getStatus().setHp(getStatus().getMaxHp() * Config.RESPAWN_RESTORE_HP);
		
		// Start broadcast status
		broadcastPacket(new Revive(this));
	}
	
	@Override
	public boolean isMovementDisabled()
	{
		return super.isMovementDisabled() || getStatus().getMoveSpeed() == 0;
	}
	
	public boolean checkIfPvP(Playable target)
	{
		if (target == null || target == this)
			return false;
		
		final Player player = getActingPlayer();
		if (player == null || player.getKarma() != 0)
			return false;
		
		final Player targetPlayer = target.getActingPlayer();
		if (targetPlayer == null || targetPlayer == this)
			return false;
		
		if (targetPlayer.getKarma() != 0 || targetPlayer.getPvpFlag() == 0)
			return false;
		
		return true;
	}
	
	/**
	 * <B><U> Overridden in </U> :</B>
	 * <ul>
	 * <li>L2Summon</li>
	 * <li>Player</li>
	 * </ul>
	 * @param id The system message to send to player.
	 */
	public void sendPacket(SystemMessageId id)
	{
		// default implementation
	}
	
	// Support for Noblesse Blessing skill, where buffs are retained after resurrect
	public final boolean isNoblesseBlessed()
	{
		return _effects.isAffected(EffectFlag.NOBLESS_BLESSING);
	}
	
	public final void stopNoblesseBlessing(AbstractEffect effect)
	{
		if (effect == null)
			stopEffects(EffectType.NOBLESSE_BLESSING);
		else
			removeEffect(effect);
		updateAbnormalEffect();
	}
	
	// Support for Soul of the Phoenix and Salvation skills
	public final boolean isPhoenixBlessed()
	{
		return _effects.isAffected(EffectFlag.PHOENIX_BLESSING);
	}
	
	public final void stopPhoenixBlessing(AbstractEffect effect)
	{
		if (effect == null)
			stopEffects(EffectType.PHOENIX_BLESSING);
		else
			removeEffect(effect);
		
		updateAbnormalEffect();
	}
	
	/**
	 * @return True if the Silent Moving mode is active.
	 */
	public boolean isSilentMoving()
	{
		return _effects.isAffected(EffectFlag.SILENT_MOVE);
	}
	
	// for Newbie Protection Blessing skill, keeps you safe from an attack by a chaotic character >= 10 levels apart from you
	public final boolean getProtectionBlessing()
	{
		return _effects.isAffected(EffectFlag.PROTECTION_BLESSING);
	}
	
	public void stopProtectionBlessing(AbstractEffect effect)
	{
		if (effect == null)
			stopEffects(EffectType.PROTECTION_BLESSING);
		else
			removeEffect(effect);
		
		updateAbnormalEffect();
	}
	
	// Charm of Luck - During a Raid/Boss war, decreased chance for death penalty
	public final boolean getCharmOfLuck()
	{
		return _effects.isAffected(EffectFlag.CHARM_OF_LUCK);
	}
	
	public final void stopCharmOfLuck(AbstractEffect effect)
	{
		if (effect == null)
			stopEffects(EffectType.CHARM_OF_LUCK);
		else
			removeEffect(effect);
		
		updateAbnormalEffect();
	}
	
	@Override
	public void updateEffectIcons(boolean partyOnly)
	{
		_effects.updateEffectIcons(partyOnly);
	}
	
	/**
	 * This method allows to easily send relations. Overridden in L2Summon and Player.
	 */
	public void broadcastRelationsChanges()
	{
	}
	
	@Override
	public boolean isInArena()
	{
		return isInsideZone(ZoneId.PVP) && !isInsideZone(ZoneId.SIEGE);
	}
	
	public void addItemSkillTimeStamp(L2Skill itemSkill, ItemInstance itemInstance)
	{
		final EtcItem etcItem = itemInstance.getEtcItem();
		final int reuseDelay = Math.max(itemSkill.getReuseDelay(), etcItem.getReuseDelay());
		
		addTimeStamp(itemSkill, reuseDelay);
		if (reuseDelay != 0)
			disableSkill(itemSkill, reuseDelay);
		
		final int group = etcItem.getSharedReuseGroup();
		if (group >= 0)
			sendPacket(new ExUseSharedGroupItem(etcItem.getItemId(), group, reuseDelay, reuseDelay));
	}
	
	/**
	 * Disable this ItemInstance id for the duration of the delay in milliseconds.
	 * @param item
	 * @param delay (seconds * 1000)
	 */
	public void disableItem(ItemInstance item, long delay)
	{
		if (item == null)
			return;
		
		_disabledItems.put(item.getObjectId(), System.currentTimeMillis() + delay);
	}
	
	/**
	 * Check if an item is disabled. All skills disabled are identified by their reuse objectIds in <B>_disabledItems</B>.
	 * @param item The ItemInstance to check
	 * @return true if the item is currently disabled.
	 */
	public boolean isItemDisabled(ItemInstance item)
	{
		if (_disabledItems.isEmpty())
			return false;
		
		if (item == null || isAllSkillsDisabled())
			return true;
		
		final int hashCode = item.getObjectId();
		
		final Long timeStamp = _disabledItems.get(hashCode);
		if (timeStamp == null)
			return false;
		
		if (timeStamp < System.currentTimeMillis())
		{
			_disabledItems.remove(hashCode);
			return false;
		}
		
		return true;
	}
	
	/**
	 * Check if the requested casting is a Pc->Pc skill cast and if it's a valid pvp condition
	 * @param target WorldObject instance containing the target
	 * @param skill L2Skill instance with the skill being casted
	 * @param isCtrlPressed Boolean signifying if the control key was used to cast
	 * @return {@code false} if the skill is a pvpSkill and target is not a valid pvp target, {@code true} otherwise.
	 */
	public boolean canCastOffensiveSkillOnPlayable(Playable target, L2Skill skill, boolean isCtrlPressed)
	{
		// No checks for players in Olympiad
		final Player targetPlayer = target.getActingPlayer();
		if (getActingPlayer().isInOlympiadMode() && targetPlayer.isInOlympiadMode() && getActingPlayer().getOlympiadGameId() == targetPlayer.getOlympiadGameId())
			return true;
		
		// No checks for players in Duel
		if (getActingPlayer().isInDuel() && targetPlayer.isInDuel() && getActingPlayer().getDuelId() == targetPlayer.getDuelId())
			return true;
		
		final boolean sameParty = (isInParty() && targetPlayer.isInParty() && getParty().getLeader() == targetPlayer.getParty().getLeader());
		final boolean sameCommandChannel = (isInParty() && targetPlayer.isInParty() && getParty().getCommandChannel() != null && getParty().getCommandChannel().containsPlayer(targetPlayer));
		final boolean sameClan = (getActingPlayer().getClanId() > 0 && getActingPlayer().getClanId() == targetPlayer.getClanId());
		final boolean sameAlliance = (getActingPlayer().getAllyId() > 0 && getActingPlayer().getAllyId() == targetPlayer.getAllyId());
		
		boolean sameSiegeSide = false;
		final Siege siege = CastleManager.getInstance().getActiveSiege(this);
		if (siege != null)
		{
			sameSiegeSide = ((siege.checkSides(targetPlayer.getClan(), SiegeSide.DEFENDER, SiegeSide.OWNER) && siege.checkSides(getActingPlayer().getClan(), SiegeSide.DEFENDER, SiegeSide.OWNER)) || (siege.checkSide(targetPlayer.getClan(), SiegeSide.ATTACKER) && siege.checkSide(getActingPlayer().getClan(), SiegeSide.ATTACKER)));
			sameSiegeSide &= target.isInsideZone(ZoneId.SIEGE) && getActingPlayer().isInsideZone(ZoneId.SIEGE);
		}
		
		// Players in the same CC/party/alliance/clan may only damage each other with ctrlPressed.
		// If it's an AOE skill, only the mainTarget will be hit. PvpFlag / Karma do not influence these checks.
		final boolean isMainTarget = getAI().getCurrentIntention().getFinalTarget() == target;
		final boolean isCtrlDamagingTheMainTarget = isCtrlPressed && skill.isDamage() && isMainTarget;
		if (sameParty || sameCommandChannel || sameClan || sameAlliance || sameSiegeSide)
			return isCtrlDamagingTheMainTarget;
		
		// If the target not from the same CC/party/alliance/clan/SiegeSide is in a PVP area, you can do anything.
		if (isInsideZone(ZoneId.PVP) && targetPlayer.isInsideZone(ZoneId.PVP))
			return true;
		
		if (targetPlayer.getProtectionBlessing() && (getActingPlayer().getStatus().getLevel() - targetPlayer.getStatus().getLevel() >= 10) && getActingPlayer().getKarma() > 0)
			return false;
		
		if (getActingPlayer().getProtectionBlessing() && (targetPlayer.getStatus().getLevel() - getActingPlayer().getStatus().getLevel() >= 10) && targetPlayer.getKarma() > 0)
			return false;
		
		if (targetPlayer.isCursedWeaponEquipped() && getActingPlayer().getStatus().getLevel() <= 20)
			return false;
		
		if (getActingPlayer().isCursedWeaponEquipped() && targetPlayer.getStatus().getLevel() <= 20)
			return false;
		
		// If the target not from the same CC/party/alliance/clan/SiegeSide is flagged / PK, you can do anything.
		if (targetPlayer.getPvpFlag() > 0 || targetPlayer.getKarma() > 0)
			return true;
			
		// If the caster not from the same CC/party/alliance/clan is at war with the target, then With CTRL he may damage and debuff.
		// CTRL is still necessary for damaging. You can do anything so long as you have CTRL pressed.
		// pvpFlag / Karma do not influence these checks
		final Clan aClan = getActingPlayer().getClan();
		final Clan tClan = targetPlayer.getClan();
		if (aClan != null && tClan != null && aClan.isAtWarWith(tClan.getClanId()) && tClan.isAtWarWith(aClan.getClanId()))
			return isCtrlPressed;
		
		// If the target not from the same CC/party/alliance/clan is white, it may be damaged with CTRL.
		final boolean isCtrlSignet = isCtrlPressed && skill.isSignetOffensiveSkill();
		return isCtrlDamagingTheMainTarget || isCtrlSignet;
	}
	
	@Override
	public boolean isAttackableBy(Creature attacker)
	{
		if (!super.isAttackableBy(attacker))
			return false;
		
		// Attackables can attack Playables anytime, anywhere
		if (attacker instanceof Monster)
			return true;
		
		// SiegeGuards cannot attack defenders/owners
		if (attacker instanceof SiegeGuard)
		{
			if (getActingPlayer().getClan() != null)
			{
				final Siege siege = CastleManager.getInstance().getActiveSiege(this);
				if (siege != null && siege.checkSides(getActingPlayer().getClan(), SiegeSide.DEFENDER, SiegeSide.OWNER))
					return false;
			}
			
			return true;
		}
		
		if (attacker instanceof Playable)
		{
			final Playable attackerPlayable = (Playable) attacker;
			
			// You cannot be attacked by a Playable in Olympiad before the start of the game.
			if (getActingPlayer().isInOlympiadMode() && !getActingPlayer().isOlympiadStart())
				return false;
			
			if (isInsideZone(ZoneId.PVP))
				return true;
			
			// One cannot be attacked if any of the two has Blessing of Protection and the other is >=10 levels higher and is PK
			if (getProtectionBlessing() && (attackerPlayable.getStatus().getLevel() - getStatus().getLevel() >= 10) && attackerPlayable.getKarma() > 0)
				return false;
			
			if (attackerPlayable.getProtectionBlessing() && (getStatus().getLevel() - attackerPlayable.getStatus().getLevel() >= 10) && getKarma() > 0)
				return false;
			
			// One cannot be attacked if any of the two is wielding a Cursed Weapon and the other is under level 20
			if (getActingPlayer().isCursedWeaponEquipped() && attackerPlayable.getStatus().getLevel() <= 20)
				return false;
			
			if (attackerPlayable.getActingPlayer().isCursedWeaponEquipped() && getStatus().getLevel() <= 20)
				return false;
		}
		
		return true;
	}
	
	@Override
	public boolean isAttackableWithoutForceBy(Playable attacker)
	{
		final Player attackerPlayer = attacker.getActingPlayer();
		if (attackerPlayer.isInOlympiadMode() && getActingPlayer().isInOlympiadMode() && getActingPlayer().isOlympiadStart() && attackerPlayer.getOlympiadGameId() == getActingPlayer().getOlympiadGameId())
			return true;
		
		if (getActingPlayer().getDuelState() == DuelState.DUELLING && getActingPlayer().getDuelId() == attackerPlayer.getDuelId())
			return true;
		
		final boolean sameParty = (isInParty() && attackerPlayer.isInParty() && getParty().getLeader() == attackerPlayer.getParty().getLeader());
		final boolean sameCommandChannel = (isInParty() && attackerPlayer.isInParty() && getParty().getCommandChannel() != null && getParty().getCommandChannel().containsPlayer(attackerPlayer));
		final boolean sameClan = (getActingPlayer().getClanId() > 0 && getActingPlayer().getClanId() == attackerPlayer.getClanId());
		final boolean sameAlliance = (getActingPlayer().getAllyId() > 0 && getActingPlayer().getAllyId() == attackerPlayer.getAllyId());
		
		boolean sameSiegeSide = false;
		final Siege siege = CastleManager.getInstance().getActiveSiege(this);
		if (siege != null)
		{
			sameSiegeSide = ((siege.checkSides(attackerPlayer.getClan(), SiegeSide.DEFENDER, SiegeSide.OWNER) && siege.checkSides(getActingPlayer().getClan(), SiegeSide.DEFENDER, SiegeSide.OWNER)) || (siege.checkSide(attackerPlayer.getClan(), SiegeSide.ATTACKER) && siege.checkSide(getActingPlayer().getClan(), SiegeSide.ATTACKER)));
			sameSiegeSide &= attackerPlayer.isInsideZone(ZoneId.SIEGE) && getActingPlayer().isInsideZone(ZoneId.SIEGE);
		}
		// Players in the same CC/party/alliance/clan cannot attack without CTRL
		if (sameParty || sameCommandChannel || sameClan || sameAlliance || sameSiegeSide)
			return false;
		
		// CTRL is not needed if both are in a PVP area
		if (isInsideZone(ZoneId.PVP) && attacker.isInsideZone(ZoneId.PVP))
			return true;
		
		// CTRL is not needed if the target (this) is flagged / PK
		if (getKarma() > 0 || getPvpFlag() > 0)
			return true;
		
		// Any other case returns false, even clan war. You need CTRL to attack.
		return false;
	}
	
	/**
	 * @param target : The {@link Creature} used as target.
	 * @return True if this {@link Playable} can continue to attack the {@link Creature} set as target, false otherwise.
	 */
	public boolean canKeepAttacking(Creature target)
	{
		if (target == null)
			return false;
		
		if (target instanceof Playable)
		{
			final Player targetPlayer = target.getActingPlayer();
			
			// Playables in Olympiad continue the attack
			if (targetPlayer.isInOlympiadMode() && getActingPlayer().isInOlympiadMode() && getActingPlayer().isOlympiadStart() && targetPlayer.getOlympiadGameId() == getActingPlayer().getOlympiadGameId())
				return true;
			
			// Playables in Duel continue the attack
			if (getActingPlayer().getDuelState() == DuelState.DUELLING && getActingPlayer().getDuelId() == targetPlayer.getDuelId())
				return true;
			
			// Playables in a PVP area continue the attack
			if (isInsideZone(ZoneId.PVP) && target.isInsideZone(ZoneId.PVP))
				return true;
			
			return false;
		}
		return true;
	}
}