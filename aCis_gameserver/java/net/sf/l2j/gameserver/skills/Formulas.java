package net.sf.l2j.gameserver.skills;

import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.math.MathUtil;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.manager.CastleManager;
import net.sf.l2j.gameserver.data.manager.ClanHallManager;
import net.sf.l2j.gameserver.data.manager.ZoneManager;
import net.sf.l2j.gameserver.data.xml.PlayerLevelData;
import net.sf.l2j.gameserver.enums.SiegeSide;
import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.enums.actors.NpcRace;
import net.sf.l2j.gameserver.enums.items.WeaponType;
import net.sf.l2j.gameserver.enums.skills.ElementType;
import net.sf.l2j.gameserver.enums.skills.SkillType;
import net.sf.l2j.gameserver.enums.skills.Stats;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.Cubic;
import net.sf.l2j.gameserver.model.actor.instance.Door;
import net.sf.l2j.gameserver.model.clanhall.ClanHall;
import net.sf.l2j.gameserver.model.clanhall.ClanHallFunction;
import net.sf.l2j.gameserver.model.entity.Siege;
import net.sf.l2j.gameserver.model.item.kind.Armor;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.model.item.kind.Weapon;
import net.sf.l2j.gameserver.model.pledge.Clan;
import net.sf.l2j.gameserver.model.zone.type.MotherTreeZone;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.skills.effects.EffectTemplate;
import net.sf.l2j.gameserver.taskmanager.GameTimeTaskManager;

public final class Formulas
{
	protected static final CLogger LOGGER = new CLogger(Formulas.class.getName());
	
	private static final int HP_REGENERATE_PERIOD = 3000; // 3 secs
	
	public static final byte SHIELD_DEFENSE_FAILED = 0; // no shield defense
	public static final byte SHIELD_DEFENSE_SUCCEED = 1; // normal shield defense
	public static final byte SHIELD_DEFENSE_PERFECT_BLOCK = 2; // perfect block
	
	public static final byte SKILL_REFLECT_FAILED = 0; // no reflect
	public static final byte SKILL_REFLECT_SUCCEED = 1; // normal reflect, some damage reflected some other not
	public static final byte SKILL_REFLECT_VENGEANCE = 2; // 100% of the damage affect both
	
	private static final byte MELEE_ATTACK_RANGE = 40;
	
	public static final int MAX_STAT_VALUE = 100;
	
	private static final double[] STR_COMPUTE = new double[]
	{
		1.036,
		34.845
	};
	private static final double[] INT_COMPUTE = new double[]
	{
		1.020,
		31.375
	};
	private static final double[] DEX_COMPUTE = new double[]
	{
		1.009,
		19.360
	};
	private static final double[] WIT_COMPUTE = new double[]
	{
		1.050,
		20.000
	};
	private static final double[] CON_COMPUTE = new double[]
	{
		1.030,
		27.632
	};
	private static final double[] MEN_COMPUTE = new double[]
	{
		1.010,
		-0.060
	};
	
	public static final double[] WIT_BONUS = new double[MAX_STAT_VALUE];
	public static final double[] MEN_BONUS = new double[MAX_STAT_VALUE];
	public static final double[] INT_BONUS = new double[MAX_STAT_VALUE];
	public static final double[] STR_BONUS = new double[MAX_STAT_VALUE];
	public static final double[] DEX_BONUS = new double[MAX_STAT_VALUE];
	public static final double[] CON_BONUS = new double[MAX_STAT_VALUE];
	
	public static final double[] BASE_EVASION_ACCURACY = new double[MAX_STAT_VALUE];
	
	protected static final double[] SQRT_MEN_BONUS = new double[MAX_STAT_VALUE];
	protected static final double[] SQRT_CON_BONUS = new double[MAX_STAT_VALUE];
	
	static
	{
		for (int i = 0; i < STR_BONUS.length; i++)
			STR_BONUS[i] = Math.floor(Math.pow(STR_COMPUTE[0], i - STR_COMPUTE[1]) * 100 + .5d) / 100;
		for (int i = 0; i < INT_BONUS.length; i++)
			INT_BONUS[i] = Math.floor(Math.pow(INT_COMPUTE[0], i - INT_COMPUTE[1]) * 100 + .5d) / 100;
		for (int i = 0; i < DEX_BONUS.length; i++)
			DEX_BONUS[i] = Math.floor(Math.pow(DEX_COMPUTE[0], i - DEX_COMPUTE[1]) * 100 + .5d) / 100;
		for (int i = 0; i < WIT_BONUS.length; i++)
			WIT_BONUS[i] = Math.floor(Math.pow(WIT_COMPUTE[0], i - WIT_COMPUTE[1]) * 100 + .5d) / 100;
		for (int i = 0; i < CON_BONUS.length; i++)
			CON_BONUS[i] = Math.floor(Math.pow(CON_COMPUTE[0], i - CON_COMPUTE[1]) * 100 + .5d) / 100;
		for (int i = 0; i < MEN_BONUS.length; i++)
			MEN_BONUS[i] = Math.floor(Math.pow(MEN_COMPUTE[0], i - MEN_COMPUTE[1]) * 100 + .5d) / 100;
		
		for (int i = 0; i < BASE_EVASION_ACCURACY.length; i++)
			BASE_EVASION_ACCURACY[i] = Math.sqrt(i) * 6;
		
		// Precompute square root values
		for (int i = 0; i < SQRT_CON_BONUS.length; i++)
			SQRT_CON_BONUS[i] = Math.sqrt(CON_BONUS[i]);
		for (int i = 0; i < SQRT_MEN_BONUS.length; i++)
			SQRT_MEN_BONUS[i] = Math.sqrt(MEN_BONUS[i]);
	}
	
	/**
	 * @param creature : The {@link Creature} to test.
	 * @return The period between 2 regenerations task.
	 */
	public static int getRegeneratePeriod(Creature creature)
	{
		if (creature instanceof Door)
			return HP_REGENERATE_PERIOD * 100; // 5 mins
			
		return HP_REGENERATE_PERIOD; // 3s
	}
	
	/**
	 * @param creature : The {@link Creature} to test.
	 * @return The HP regen rate (base + modifiers).
	 */
	public static final double calcHpRegen(Creature creature)
	{
		double init = creature.getTemplate().getBaseHpReg();
		double hpRegenMultiplier = (creature.isRaidRelated()) ? Config.RAID_HP_REGEN_MULTIPLIER : Config.HP_REGEN_MULTIPLIER;
		double hpRegenBonus = 0;
		
		if (creature instanceof Player)
		{
			Player player = (Player) creature;
			
			// Calculate correct baseHpReg value for certain level of PC
			init += (player.getStatus().getLevel() > 10) ? ((player.getStatus().getLevel() - 1) / 10.0) : 0.5;
			
			if (calcSiegeRegenModifer(player))
				hpRegenMultiplier *= 1.5;
			
			if (player.isInsideZone(ZoneId.CLAN_HALL) && player.getClan() != null)
			{
				final int chId = player.getClan().getClanHallId();
				if (chId > 0)
				{
					final ClanHall ch = ClanHallManager.getInstance().getClanHall(chId);
					if (ch != null)
					{
						final ClanHallFunction chf = ch.getFunction(ClanHall.FUNC_RESTORE_HP);
						if (chf != null)
							hpRegenMultiplier *= 1 + chf.getLvl() / 100.0;
					}
				}
			}
			
			// Mother Tree effect is calculated at last
			if (player.isInsideZone(ZoneId.MOTHER_TREE))
			{
				final MotherTreeZone zone = ZoneManager.getInstance().getZone(player, MotherTreeZone.class);
				if (zone != null)
					hpRegenBonus += zone.getHpRegenBonus();
			}
			
			// Calculate Movement bonus
			if (player.isSitting())
				hpRegenMultiplier *= 1.5;
			else if (!player.isMoving())
				hpRegenMultiplier *= 1.1;
			else if (player.isRunning())
				hpRegenMultiplier *= 0.7;
		}
		// Add CON bonus
		init *= creature.getStatus().getLevelMod() * CON_BONUS[creature.getStatus().getCON()];
		
		if (init < 1)
			init = 1;
		
		return creature.getStatus().calcStat(Stats.REGENERATE_HP_RATE, init, null, null) * hpRegenMultiplier + hpRegenBonus;
	}
	
	/**
	 * @param creature : The {@link Creature} to test.
	 * @return The MP regen rate (base + modifiers).
	 */
	public static final double calcMpRegen(Creature creature)
	{
		double init = creature.getTemplate().getBaseMpReg();
		double mpRegenMultiplier = (creature.isRaidRelated()) ? Config.RAID_MP_REGEN_MULTIPLIER : Config.MP_REGEN_MULTIPLIER;
		double mpRegenBonus = 0;
		
		if (creature instanceof Player)
		{
			Player player = (Player) creature;
			
			// Calculate correct baseMpReg value for certain level of PC
			init += 0.3 * ((player.getStatus().getLevel() - 1) / 10.0);
			
			// Mother Tree effect is calculated at last
			if (player.isInsideZone(ZoneId.MOTHER_TREE))
			{
				final MotherTreeZone zone = ZoneManager.getInstance().getZone(player, MotherTreeZone.class);
				if (zone != null)
					mpRegenBonus += zone.getMpRegenBonus();
			}
			
			if (player.isInsideZone(ZoneId.CLAN_HALL) && player.getClan() != null)
			{
				final int chId = player.getClan().getClanHallId();
				if (chId > 0)
				{
					final ClanHall ch = ClanHallManager.getInstance().getClanHall(chId);
					if (ch != null)
					{
						final ClanHallFunction chf = ch.getFunction(ClanHall.FUNC_RESTORE_MP);
						if (chf != null)
							mpRegenMultiplier *= 1 + chf.getLvl() / 100.0;
					}
				}
			}
			
			// Calculate Movement bonus
			if (player.isSitting())
				mpRegenMultiplier *= 1.5;
			else if (!player.isMoving())
				mpRegenMultiplier *= 1.1;
			else if (player.isRunning())
				mpRegenMultiplier *= 0.7;
		}
		// Add MEN bonus
		init *= creature.getStatus().getLevelMod() * MEN_BONUS[creature.getStatus().getMEN()];
		
		if (init < 1)
			init = 1;
		
		return creature.getStatus().calcStat(Stats.REGENERATE_MP_RATE, init, null, null) * mpRegenMultiplier + mpRegenBonus;
	}
	
	/**
	 * @param player : The {@link Player} to test.
	 * @return The CP regen rate (base + modifiers).
	 */
	public static final double calcCpRegen(Player player)
	{
		// Calculate correct baseHpReg value for certain level of PC
		double init = player.getTemplate().getBaseHpReg() + ((player.getStatus().getLevel() > 10) ? ((player.getStatus().getLevel() - 1) / 10.0) : 0.5);
		
		// Calculate Movement bonus
		double cpRegenMultiplier = Config.CP_REGEN_MULTIPLIER;
		if (player.isSitting())
			cpRegenMultiplier *= 1.5;
		else if (!player.isMoving())
			cpRegenMultiplier *= 1.1;
		else if (player.isRunning())
			cpRegenMultiplier *= 0.7;
		
		// Apply CON bonus
		init *= player.getStatus().getLevelMod() * CON_BONUS[player.getStatus().getCON()];
		
		if (init < 1)
			init = 1;
		
		return player.getStatus().calcStat(Stats.REGENERATE_CP_RATE, init, null, null) * cpRegenMultiplier;
	}
	
	/**
	 * @param player : The {@link Player} to test.
	 * @return True if the {@link Player} is near his {@link Clan} HQ, or false otherwise.
	 */
	public static final boolean calcSiegeRegenModifer(Player player)
	{
		if (player == null)
			return false;
		
		final Clan clan = player.getClan();
		if (clan == null)
			return false;
		
		final Siege siege = CastleManager.getInstance().getActiveSiege(player);
		if (siege == null || !siege.checkSide(clan, SiegeSide.ATTACKER))
			return false;
		
		final Npc flag = clan.getFlag();
		return flag != null && player.isIn3DRadius(flag, 200);
	}
	
	/**
	 * Calculate the blow success rate, based on {@link Creature} attacker, {@link Creature} target and {@link L2Skill}.
	 * @param attacker : The {@link Creature} attacker.
	 * @param target : The {@link Creature} target.
	 * @param skill : The {@link L2Skill} to use.
	 * @return True if successful, false otherwise.
	 */
	public static final boolean calcBlowRate(Creature attacker, Creature target, L2Skill skill)
	{
		double baseRate = skill.getBaseLandRate();
		if (baseRate == 0.0)
			return false;
		
		final boolean isAttackerInFrontofTarget = attacker.isInFrontOf(target);
		final boolean isBackstab = skill.getId() == 30;
		
		if (isAttackerInFrontofTarget && isBackstab)
		{
			if (Config.DEVELOPER)
			{
				StringUtil.printSection("Blow Rate");
				LOGGER.info("Final blow rate overriden by backstab under front target: 30 / 1000");
			}
			return 30 > Rnd.get(1000);
		}
		
		final double dexMul = DEX_BONUS[attacker.getStatus().getDEX()];
		final double blowMul = attacker.getStatus().calcStat(Stats.BLOW_RATE, 1, target, null);
		final double posMul = (attacker.isBehind(target)) ? 1.15 : ((isAttackerInFrontofTarget) ? 1. : 1.1);
		
		final double blowRate = baseRate * 2 * dexMul * blowMul * posMul;
		
		if (Config.DEVELOPER)
		{
			StringUtil.printSection("Blow Rate");
			LOGGER.info("Basic values: baseRate: {}", baseRate);
			LOGGER.info("Multipliers: dex: {}, blow: {}, pos: {}", dexMul, blowMul, posMul);
			LOGGER.info("Final blow rate: {} / 1000", blowRate);
		}
		
		return Math.min(blowRate, (isBackstab) ? 1000 : 800) > Rnd.get(1000);
	}
	
	/**
	 * Calculate the lethal success rate, based on {@link Creature} attacker, {@link Creature} target and few {@link L2Skill} parameters.
	 * @param attacker : The {@link Creature} attacker.
	 * @param target : The {@link Creature} target.
	 * @param baseRate : The base lethal chance of the {@link L2Skill}.
	 * @param magiclvl : The associated magic {@link L2Skill} level.
	 * @return True if successful, false otherwise.
	 */
	private static final boolean calcLethalRate(Creature attacker, Creature target, double baseRate, int magiclvl)
	{
		final double lethalMul = attacker.getStatus().calcStat(Stats.LETHAL_RATE, 1, target, null);
		
		final int attackerLevel = attacker.getStatus().getLevel();
		final int targetLevel = target.getStatus().getLevel();
		
		double editedRate;
		if (magiclvl > 0)
		{
			final int delta = ((magiclvl + attackerLevel) / 2) - 1 - targetLevel;
			if (delta >= -3)
				editedRate = baseRate * attackerLevel / targetLevel;
			else if (delta < -3 && delta >= -9)
				editedRate = baseRate / delta * -3;
			else
				editedRate = baseRate / 15;
		}
		else
			editedRate = baseRate * attackerLevel / targetLevel;
		
		final double lethalRate = editedRate * 10 * lethalMul;
		
		if (Config.DEVELOPER)
		{
			StringUtil.printSection("Lethal Rate");
			LOGGER.info("Basic values: baseRate: {}, editedRate: {}", baseRate, editedRate);
			LOGGER.info("Multipliers: lethal: {}", lethalMul);
			LOGGER.info("Final lethal rate: {} / 1000", lethalRate);
		}
		
		return lethalRate > Rnd.get(1000);
	}
	
	public static final void calcLethalHit(Creature attacker, Creature target, L2Skill skill)
	{
		// If the attacker can't attack, return.
		if (attacker instanceof Player && !((Player) attacker).getAccessLevel().canGiveDamage())
			return;
		
		// If the target is invulnerable, related to RaidBoss or a Door, return.
		if (target.isInvul() || target.isRaidRelated() || target instanceof Door)
			return;
		
		// If one of following IDs is found, return (Tyrannosaurus x 3, Headquarters).
		if (target instanceof Npc)
		{
			switch (((Npc) target).getNpcId())
			{
				case 22215:
				case 22216:
				case 22217:
				case 35062:
					return;
			}
		}
		
		// Second lethal effect (hp to 1 for npc, cp/hp to 1 for player).
		if (skill.getLethalChance2() > 0 && calcLethalRate(attacker, target, skill.getLethalChance2(), skill.getMagicLevel()))
		{
			if (target instanceof Npc)
				target.reduceCurrentHp(target.getStatus().getHp() - 1, attacker, skill);
			else if (target instanceof Player)
			{
				final Player targetPlayer = (Player) target;
				targetPlayer.getStatus().setHp(1, false);
				targetPlayer.getStatus().setCp(1);
				targetPlayer.sendPacket(SystemMessageId.LETHAL_STRIKE);
			}
			attacker.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.LETHAL_STRIKE_SUCCESSFUL));
		}
		// First lethal effect (hp/2 for npc, cp to 1 for player).
		else if (skill.getLethalChance1() > 0 && calcLethalRate(attacker, target, skill.getLethalChance1(), skill.getMagicLevel()))
		{
			if (target instanceof Npc)
				target.reduceCurrentHp(target.getStatus().getHp() / 2, attacker, skill);
			else if (target instanceof Player)
			{
				final Player targetPlayer = (Player) target;
				targetPlayer.getStatus().setCp(1);
				targetPlayer.sendPacket(SystemMessageId.LETHAL_STRIKE);
			}
			attacker.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.LETHAL_STRIKE_SUCCESSFUL));
		}
	}
	
	/**
	 * @param attacker The {@link Creature} launching the blow.
	 * @param target : The {@link Creature} victim of the blow.
	 * @param skill : The {@link L2Skill} to test.
	 * @param shld : True if the {@link Creature} target is wearing a shield, false otherwise.
	 * @param ss : True if ss are activated, false otherwise.
	 * @return The calculated blow damage.
	 */
	public static double calcBlowDamage(Creature attacker, Creature target, L2Skill skill, byte shld, boolean ss)
	{
		double defence = target.getStatus().getPDef(attacker);
		switch (shld)
		{
			case SHIELD_DEFENSE_SUCCEED:
				defence += target.getStatus().getShldDef();
				break;
			
			case SHIELD_DEFENSE_PERFECT_BLOCK:
				return 1.;
		}
		
		final boolean isPvP = attacker instanceof Playable && target instanceof Playable;
		
		double attackPower = attacker.getStatus().getPAtk(target);
		double skillPower = skill.getPower();
		final double addCritPower = attacker.getStatus().calcStat(Stats.CRITICAL_DAMAGE_ADD, 0, target, skill) * 6;
		
		if (ss)
		{
			attackPower *= 2.;
			
			if (skill.getSSBoost() > 0)
				skillPower *= skill.getSSBoost();
		}
		
		final double critDamMul = attacker.getStatus().calcStat(Stats.CRITICAL_DAMAGE, 1, target, skill);
		final double rndMul = Rnd.get(95, 105) / 100.;
		final double critDamPosMul = ((attacker.getStatus().calcStat(Stats.CRITICAL_DAMAGE_POS, 1, target, skill) - 1) / 2 + 1); // Divided by 2 for blow types.
		final double posMul = (attacker.isBehind(target)) ? 1.1 : ((attacker.isInFrontOf(target)) ? 1. : 1.025); // Divided by 2 for blow types.
		final double pvpMul = (isPvP) ? attacker.getStatus().calcStat(Stats.PVP_PHYS_SKILL_DMG, 1, null, null) : 1.;
		
		final double critVuln = target.getStatus().calcStat(Stats.CRIT_VULN, 1, target, skill);
		final double daggerVuln = target.getStatus().calcStat(Stats.DAGGER_WPN_VULN, 1, target, null);
		
		final double damage = ((attackPower + skillPower) * critDamMul * rndMul * critDamPosMul * posMul * pvpMul * critVuln * daggerVuln + addCritPower) * ((isPvP) ? 70. : 77.) / defence;
		
		if (Config.DEVELOPER)
		{
			StringUtil.printSection("Blow damage");
			LOGGER.info("ss:{}, shield:{}, isPvp:{}, defence:{}", ss, shld, isPvP, defence);
			LOGGER.info("Basic powers: attack: {}, skill: {}, addCrit: {}", attackPower, skillPower, addCritPower);
			LOGGER.info("Multipliers: critDam: {}, rnd: {}, critPos: {}, pos: {}, pvp: {}", critDamMul, rndMul, critDamPosMul, posMul, pvpMul);
			LOGGER.info("Vulnerabilities: criticalVuln: {}, daggerVuln: {}", critVuln, daggerVuln);
			LOGGER.info("Final blow damage: {}", damage);
		}
		return Math.max(1, damage);
	}
	
	/**
	 * Calculated damage caused by ATTACK of attacker on target, called separatly for each weapon, if dual-weapon is used.
	 * @param attacker player or NPC that makes ATTACK
	 * @param target player or NPC, target of ATTACK
	 * @param skill skill used.
	 * @param shld target was using a shield or not.
	 * @param crit if the ATTACK have critical success
	 * @param ss if weapon item was charged by soulshot
	 * @return damage points
	 */
	public static final double calcPhysDam(Creature attacker, Creature target, L2Skill skill, byte shld, boolean crit, boolean ss)
	{
		if (attacker instanceof Player)
		{
			Player pcInst = (Player) attacker;
			if (pcInst.isGM() && !pcInst.getAccessLevel().canGiveDamage())
				return 0;
		}
		
		double defence = target.getStatus().getPDef(attacker);
		switch (shld)
		{
			case SHIELD_DEFENSE_SUCCEED:
				defence += target.getStatus().getShldDef();
				break;
			
			case SHIELD_DEFENSE_PERFECT_BLOCK:
				return 1.;
		}
		
		final boolean isPvP = attacker instanceof Playable && target instanceof Playable;
		double damage = attacker.getStatus().getPAtk(target);
		
		damage += calcValakasAttribute(attacker, target, skill);
		
		if (ss)
			damage *= 2;
		
		if (skill != null)
		{
			double skillpower = skill.getPower(attacker);
			
			final float ssBoost = skill.getSSBoost();
			if (ssBoost > 0 && ss)
				skillpower *= ssBoost;
			
			damage += skillpower;
		}
		
		if (crit)
		{
			// Finally retail like formula
			damage = 2 * attacker.getStatus().calcStat(Stats.CRITICAL_DAMAGE, 1, target, skill) * attacker.getStatus().calcStat(Stats.CRITICAL_DAMAGE_POS, 1, target, skill) * target.getStatus().calcStat(Stats.CRIT_VULN, 1, target, null) * (70 * damage / defence);
			// Crit dmg add is almost useless in normal hits...
			damage += (attacker.getStatus().calcStat(Stats.CRITICAL_DAMAGE_ADD, 0, target, skill) * 70 / defence);
		}
		else
			damage = 70 * damage / defence;
		
		// defence modifier depending of the attacker weapon
		final Weapon weapon = attacker.getActiveWeaponItem();
		if (weapon != null)
		{
			final Stats stat = weapon.getItemType().getVulnStat();
			if (stat != null)
				damage = target.getStatus().calcStat(stat, damage, target, null);
		}
		
		// Weapon random damage ; invalid for CHARGEDAM skills.
		if (skill == null || skill.getEffectType() != SkillType.CHARGEDAM)
			damage *= attacker.getRandomDamageMultiplier();
		
		if (target instanceof Npc)
		{
			final NpcRace race = ((Npc) target).getTemplate().getRace();
			if (race.getAtkStat() != null && race.getResStat() != null)
				damage *= 1 + ((attacker.getStatus().calcStat(race.getAtkStat(), 1, target, null) - target.getStatus().calcStat(race.getResStat(), 1, target, null)) / 100);
		}
		
		if (damage > 0 && damage < 1)
			damage = 1;
		else if (damage < 0)
			damage = 0;
		
		// Dmg bonuses in PvP fight
		if (isPvP)
		{
			if (skill == null)
				damage *= attacker.getStatus().calcStat(Stats.PVP_PHYSICAL_DMG, 1, null, null);
			else
				damage *= attacker.getStatus().calcStat(Stats.PVP_PHYS_SKILL_DMG, 1, null, null);
		}
		
		// Weapon elemental damages
		damage += calcElemental(attacker, target, null);
		
		return damage;
	}
	
	public static final double calcMagicDam(Creature attacker, Creature target, L2Skill skill, byte shld, boolean ss, boolean bss, boolean mcrit)
	{
		if (attacker instanceof Player)
		{
			Player pcInst = (Player) attacker;
			if (pcInst.isGM() && !pcInst.getAccessLevel().canGiveDamage())
				return 0;
		}
		
		double mDef = target.getStatus().getMDef(attacker, skill);
		switch (shld)
		{
			case SHIELD_DEFENSE_SUCCEED:
				mDef += target.getStatus().getShldDef();
				break;
			
			case SHIELD_DEFENSE_PERFECT_BLOCK:
				return 1.;
		}
		
		double mAtk = attacker.getStatus().getMAtk(target, skill);
		
		if (bss)
			mAtk *= 4;
		else if (ss)
			mAtk *= 2;
		
		double damage = 91 * Math.sqrt(mAtk) / mDef * skill.getPower(attacker);
		
		// Failure calculation
		if (Config.MAGIC_FAILURES && !calcMagicSuccess(attacker, target, skill))
		{
			if (attacker instanceof Player)
			{
				if (calcMagicSuccess(attacker, target, skill) && (target.getStatus().getLevel() - attacker.getStatus().getLevel()) <= 9)
				{
					if (skill.getSkillType() == SkillType.DRAIN)
						attacker.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.DRAIN_HALF_SUCCESFUL));
					else
						attacker.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.ATTACK_FAILED));
					
					damage /= 2;
				}
				else
				{
					attacker.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_RESISTED_YOUR_S2).addCharName(target).addSkillName(skill));
					damage = 1;
				}
			}
			
			if (target instanceof Player)
			{
				if (skill.getSkillType() == SkillType.DRAIN)
					target.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.RESISTED_S1_DRAIN).addCharName(attacker));
				else
					target.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.RESISTED_S1_MAGIC).addCharName(attacker));
			}
		}
		else if (mcrit)
			damage *= 4;
		
		// Pvp bonuses for dmg
		if (attacker instanceof Playable && target instanceof Playable)
		{
			if (skill.isMagic())
				damage *= attacker.getStatus().calcStat(Stats.PVP_MAGICAL_DMG, 1, null, null);
			else
				damage *= attacker.getStatus().calcStat(Stats.PVP_PHYS_SKILL_DMG, 1, null, null);
		}
		
		damage *= calcElemental(attacker, target, skill);
		
		return damage;
	}
	
	public static final double calcMagicDam(Cubic attacker, Creature target, L2Skill skill, boolean mcrit, byte shld)
	{
		double mDef = target.getStatus().getMDef(attacker.getOwner(), skill);
		switch (shld)
		{
			case SHIELD_DEFENSE_SUCCEED:
				mDef += target.getStatus().getShldDef();
				break;
			
			case SHIELD_DEFENSE_PERFECT_BLOCK:
				return 1.;
		}
		
		double damage = 91 / mDef * skill.getPower();
		Player owner = attacker.getOwner();
		
		// Failure calculation
		if (Config.MAGIC_FAILURES && !calcMagicSuccess(owner, target, skill))
		{
			if (calcMagicSuccess(owner, target, skill) && (target.getStatus().getLevel() - skill.getMagicLevel()) <= 9)
			{
				if (skill.getSkillType() == SkillType.DRAIN)
					owner.sendPacket(SystemMessageId.DRAIN_HALF_SUCCESFUL);
				else
					owner.sendPacket(SystemMessageId.ATTACK_FAILED);
				
				damage /= 2;
			}
			else
			{
				owner.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_RESISTED_YOUR_S2).addCharName(target).addSkillName(skill));
				damage = 1;
			}
			
			if (target instanceof Player)
			{
				if (skill.getSkillType() == SkillType.DRAIN)
					target.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.RESISTED_S1_DRAIN).addCharName(owner));
				else
					target.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.RESISTED_S1_MAGIC).addCharName(owner));
			}
		}
		else if (mcrit)
			damage *= 4;
		
		damage *= calcElemental(owner, target, skill);
		
		return damage;
	}
	
	/**
	 * @param actor
	 * @param target
	 * @param skill
	 * @return true in case of critical hit
	 */
	public static final boolean calcCrit(Creature actor, Creature target, L2Skill skill)
	{
		return calcCrit(actor.getStatus().getCriticalHit(target, skill));
	}
	
	public static final boolean calcCrit(double rate)
	{
		return rate > Rnd.get(1000);
	}
	
	public static final boolean calcMCrit(Creature actor, Creature target, L2Skill skill)
	{
		final int mRate = actor.getStatus().getMCriticalHit(target, skill);
		
		if (Config.DEVELOPER)
			LOGGER.info("Current mCritRate: {} / 1000.", mRate);
		
		return mRate > Rnd.get(1000);
	}
	
	/**
	 * Check if casting process is canceled due to hit.
	 * @param target The target to make checks on.
	 * @param dmg The amount of dealt damages.
	 */
	public static final void calcCastBreak(Creature target, double dmg)
	{
		// Don't go further for invul characters or raid bosses.
		if (target.isRaidRelated() || target.isInvul())
			return;
		
		// Break automatically the skill cast if under attack.
		if (target.getFusionSkill() != null)
		{
			target.getCast().interrupt();
			return;
		}
		
		// Don't go further for ppl casting a physical skill.
		if (target.getCast().getCurrentSkill() != null && !target.getCast().getCurrentSkill().isMagic())
			return;
		
		// Calculate all modifiers for ATTACK_CANCEL ; chance to break is higher with higher dmg, and is affected by target MEN.
		double rate = target.getStatus().calcStat(Stats.ATTACK_CANCEL, 15 + Math.sqrt(13 * dmg) - (MEN_BONUS[target.getStatus().getMEN()] * 100 - 100), null, null);
		
		if (Config.DEVELOPER)
		{
			StringUtil.printSection("Cast break rate");
			LOGGER.info("Final cast break rate: {}%.", rate);
		}
		
		if (MathUtil.limit((int) rate, 1, 99) > Rnd.get(100))
			target.getCast().interrupt();
	}
	
	/**
	 * @param attacker : The {@link Creature} who attacks.
	 * @return The delay, in ms, before the next attack.
	 */
	public static final int calculateTimeBetweenAttacks(Creature attacker)
	{
		return Math.max(100, 500000 / attacker.getStatus().getPAtkSpd());
	}
	
	/**
	 * Calculate delay (in milliseconds) for skills cast.
	 * @param attacker
	 * @param skill used to know if skill is magic or no.
	 * @param skillTime
	 * @return delay in ms.
	 */
	public static final int calcAtkSpd(Creature attacker, L2Skill skill, double skillTime)
	{
		if (skill.isMagic())
			return (int) (skillTime * 333 / attacker.getStatus().getMAtkSpd());
		
		return (int) (skillTime * 333 / attacker.getStatus().getPAtkSpd());
	}
	
	/**
	 * Calculate the hit/miss chance.
	 * @param attacker : The {@link Creature} attacker to test.
	 * @param target : The {@link Creature} target to test.
	 * @return True if the hit missed or false if it evaded.
	 */
	public static boolean calcHitMiss(Creature attacker, Creature target)
	{
		int diff = attacker.getStatus().getAccuracy() - target.getStatus().getEvasionRate(attacker);
		
		// Get high or low Z bonus.
		final int diffZ = attacker.getZ() - target.getZ();
		if (diffZ > 50)
			diff += 3;
		else if (diffZ < -50)
			diff -= 3;
		
		// Get weather bonus.
		if (GameTimeTaskManager.getInstance().isNight())
			diff -= 10;
		
		// Get position bonus.
		if (attacker.isBehind(target))
			diff += 10;
		else if (!attacker.isInFrontOf(target))
			diff += 5;
		
		int chance = (90 + (2 * (diff))) * 10;
		
		if (Config.DEVELOPER)
			LOGGER.info("calcHitMiss diff: {}, rate: {}%.", diff, chance / 10);
		
		return MathUtil.limit(chance, 300, 980) < Rnd.get(1000);
	}
	
	/**
	 * Test the shield use.
	 * @param attacker The attacker.
	 * @param target The victim ; make check about his shield.
	 * @param skill The skill the attacker has used.
	 * @return 0 = shield defense doesn't succeed<br>
	 *         1 = shield defense succeed<br>
	 *         2 = perfect block
	 */
	public static byte calcShldUse(Creature attacker, Creature target, L2Skill skill)
	{
		// Ignore shield skills types bypass the shield use.
		if (skill != null && skill.ignoreShield())
			return 0;
		
		final Item item = target.getSecondaryWeaponItem();
		if (!(item instanceof Armor))
			return 0;
		
		double shldRate = target.getStatus().calcStat(Stats.SHIELD_RATE, 0, attacker, null) * DEX_BONUS[target.getStatus().getDEX()];
		if (shldRate == 0.0)
			return 0;
		
		int degreeside = (int) target.getStatus().calcStat(Stats.SHIELD_DEFENCE_ANGLE, 120, null, null);
		if (degreeside < 360 && (!target.isFacing(attacker, degreeside)))
			return 0;
		
		byte shldSuccess = SHIELD_DEFENSE_FAILED;
		
		// If attacker use bow and target wear shield, the shield block rate is multiplied by 3.
		if (attacker.getAttackType() == WeaponType.BOW)
			shldRate *= 3;
		
		if (shldRate > 0 && 100 - Config.PERFECT_SHIELD_BLOCK_RATE < Rnd.get(100))
			shldSuccess = SHIELD_DEFENSE_PERFECT_BLOCK;
		else if (shldRate > Rnd.get(100))
			shldSuccess = SHIELD_DEFENSE_SUCCEED;
		
		if (target instanceof Player)
		{
			switch (shldSuccess)
			{
				case SHIELD_DEFENSE_SUCCEED:
					((Player) target).sendPacket(SystemMessageId.SHIELD_DEFENCE_SUCCESSFULL);
					break;
				
				case SHIELD_DEFENSE_PERFECT_BLOCK:
					((Player) target).sendPacket(SystemMessageId.YOUR_EXCELLENT_SHIELD_DEFENSE_WAS_A_SUCCESS);
					break;
			}
		}
		
		return shldSuccess;
	}
	
	public static boolean calcMagicAffected(Creature actor, Creature target, L2Skill skill)
	{
		SkillType type = skill.getSkillType();
		if (target.isRaidRelated() && !calcRaidAffected(type))
			return false;
		
		double defence = 0;
		
		if (skill.isActive() && skill.isOffensive())
			defence = target.getStatus().getMDef(actor, skill);
		
		double attack = 2 * actor.getStatus().getMAtk(target, skill) * calcSkillVulnerability(actor, target, skill, type);
		double d = (attack - defence) / (attack + defence);
		
		d += 0.5 * Rnd.nextGaussian();
		return d > 0;
	}
	
	public static double calcSkillVulnerability(Creature attacker, Creature target, L2Skill skill, SkillType type)
	{
		double multiplier = 1;
		
		// Get the elemental damages.
		if (skill.getElement() != ElementType.NONE)
			multiplier *= Math.sqrt(calcElemental(attacker, target, skill));
		
		// Get the skillType to calculate its effect in function of base stats of the target.
		switch (type)
		{
			case BLEED:
				multiplier = target.getStatus().calcStat(Stats.BLEED_VULN, multiplier, target, null);
				break;
			
			case POISON:
				multiplier = target.getStatus().calcStat(Stats.POISON_VULN, multiplier, target, null);
				break;
			
			case STUN:
				multiplier = target.getStatus().calcStat(Stats.STUN_VULN, multiplier, target, null);
				break;
			
			case PARALYZE:
				multiplier = target.getStatus().calcStat(Stats.PARALYZE_VULN, multiplier, target, null);
				break;
			
			case ROOT:
				multiplier = target.getStatus().calcStat(Stats.ROOT_VULN, multiplier, target, null);
				break;
			
			case SLEEP:
				multiplier = target.getStatus().calcStat(Stats.SLEEP_VULN, multiplier, target, null);
				break;
			
			case MUTE:
			case FEAR:
			case BETRAY:
			case AGGDEBUFF:
			case AGGREDUCE_CHAR:
			case ERASE:
			case CONFUSION:
				multiplier = target.getStatus().calcStat(Stats.DERANGEMENT_VULN, multiplier, target, null);
				break;
			
			case DEBUFF:
			case WEAKNESS:
				multiplier = target.getStatus().calcStat(Stats.DEBUFF_VULN, multiplier, target, null);
				break;
			
			case CANCEL:
				multiplier = target.getStatus().calcStat(Stats.CANCEL_VULN, multiplier, target, null);
				break;
		}
		
		// Return a multiplier (exemple with resist shock : 1 + (-0,4 stun vuln) = 0,6%
		return multiplier;
	}
	
	private static double calcSkillStatModifier(SkillType type, Creature target)
	{
		double multiplier = 1;
		
		switch (type)
		{
			case STUN:
			case BLEED:
			case POISON:
				multiplier = 2 - SQRT_CON_BONUS[target.getStatus().getCON()];
				break;
			
			case SLEEP:
			case DEBUFF:
			case WEAKNESS:
			case ERASE:
			case ROOT:
			case MUTE:
			case FEAR:
			case BETRAY:
			case CONFUSION:
			case AGGREDUCE_CHAR:
			case PARALYZE:
				multiplier = 2 - SQRT_MEN_BONUS[target.getStatus().getMEN()];
				break;
		}
		
		return Math.max(0, multiplier);
	}
	
	public static double getSTRBonus(Creature activeChar)
	{
		return STR_BONUS[activeChar.getStatus().getSTR()];
	}
	
	private static double getLevelModifier(Creature attacker, Creature target, L2Skill skill)
	{
		if (skill.getLevelDepend() == 0)
			return 1;
		
		int delta = (skill.getMagicLevel() > 0 ? skill.getMagicLevel() : attacker.getStatus().getLevel()) + skill.getLevelDepend() - target.getStatus().getLevel();
		return 1 + ((delta < 0 ? 0.01 : 0.005) * delta);
	}
	
	private static double getMatkModifier(Creature attacker, Creature target, L2Skill skill, boolean bss)
	{
		double mAtkModifier = 1;
		
		if (skill.isMagic())
		{
			final double mAtk = attacker.getStatus().getMAtk(target, skill);
			double val = mAtk;
			if (bss)
				val = mAtk * 4.0;
			
			mAtkModifier = (Math.sqrt(val) / target.getStatus().getMDef(attacker, skill)) * 11.0;
		}
		return mAtkModifier;
	}
	
	public static boolean calcEffectSuccess(Creature attacker, Creature target, EffectTemplate effect, L2Skill skill, boolean bss)
	{
		final SkillType type = effect.getEffectType();
		final double baseChance = effect.getEffectPower();
		
		if (type == null)
			return Rnd.get(100) < baseChance;
		
		if (type.equals(SkillType.CANCEL)) // CANCEL type lands always
			return true;
		
		final double statModifier = calcSkillStatModifier(type, target);
		final double skillModifier = calcSkillVulnerability(attacker, target, skill, type);
		final double mAtkModifier = getMatkModifier(attacker, target, skill, bss);
		final double lvlModifier = getLevelModifier(attacker, target, skill);
		final double rate = Math.max(1, Math.min((baseChance * statModifier * skillModifier * mAtkModifier * lvlModifier), 99));
		
		if (Config.DEVELOPER)
			LOGGER.info("calcEffectSuccess(): name:{} eff.type:{} power:{} statMod:{} skillMod:{} mAtkMod:{} lvlMod:{} total:{}%.", skill.getName(), type.toString(), baseChance, String.format("%1.2f", statModifier), String.format("%1.2f", skillModifier), String.format("%1.2f", mAtkModifier), String.format("%1.2f", lvlModifier), String.format("%1.2f", rate));
		
		return (Rnd.get(100) < rate);
	}
	
	public static boolean calcSkillSuccess(Creature attacker, Creature target, L2Skill skill, byte shld, boolean bss)
	{
		if (shld == SHIELD_DEFENSE_PERFECT_BLOCK)
			return false;
		
		final SkillType type = skill.getEffectType();
		
		if (target.isRaidRelated() && !calcRaidAffected(type))
			return false;
		
		final double baseChance = skill.getEffectPower();
		if (skill.ignoreResists())
			return (Rnd.get(100) < baseChance);
		
		final double statModifier = calcSkillStatModifier(type, target);
		final double skillModifier = calcSkillVulnerability(attacker, target, skill, type);
		final double mAtkModifier = getMatkModifier(attacker, target, skill, bss);
		final double lvlModifier = getLevelModifier(attacker, target, skill);
		final double rate = Math.max(1, Math.min((baseChance * statModifier * skillModifier * mAtkModifier * lvlModifier), 99));
		
		if (Config.DEVELOPER)
			LOGGER.info("calcSkillSuccess(): name:{} type:{} power:{} statMod:{} skillMod:{} mAtkMod:{} lvlMod:{} total:{}%.", skill.getName(), skill.getSkillType().toString(), baseChance, String.format("%1.2f", statModifier), String.format("%1.2f", skillModifier), String.format("%1.2f", mAtkModifier), String.format("%1.2f", lvlModifier), String.format("%1.2f", rate));
		
		return (Rnd.get(100) < rate);
	}
	
	public static boolean calcCubicSkillSuccess(Cubic attacker, Creature target, L2Skill skill, byte shld, boolean bss)
	{
		// if target reflect this skill then the effect will fail
		if (calcSkillReflect(target, skill) != SKILL_REFLECT_FAILED)
			return false;
		
		if (shld == SHIELD_DEFENSE_PERFECT_BLOCK)
			return false;
		
		final SkillType type = skill.getEffectType();
		
		if (target.isRaidRelated() && !calcRaidAffected(type))
			return false;
		
		final double baseChance = skill.getEffectPower();
		
		if (skill.ignoreResists())
			return Rnd.get(100) < baseChance;
		
		double mAtkModifier = 1;
		
		// Add Matk/Mdef Bonus
		if (skill.isMagic())
		{
			final double mAtk = attacker.getMAtk();
			double val = mAtk;
			if (bss)
				val = mAtk * 4.0;
			
			mAtkModifier = (Math.sqrt(val) / target.getStatus().getMDef(null, null)) * 11.0;
		}
		
		final double statModifier = calcSkillStatModifier(type, target);
		final double skillModifier = calcSkillVulnerability(attacker.getOwner(), target, skill, type);
		final double lvlModifier = getLevelModifier(attacker.getOwner(), target, skill);
		final double rate = Math.max(1, Math.min((baseChance * statModifier * skillModifier * mAtkModifier * lvlModifier), 99));
		
		if (Config.DEVELOPER)
			LOGGER.info("calcCubicSkillSuccess(): name:{} type:{} power:{} statMod:{} skillMod:{} mAtkMod:{} lvlMod:{} total:{}%.", skill.getName(), skill.getSkillType().toString(), baseChance, String.format("%1.2f", statModifier), String.format("%1.2f", skillModifier), String.format("%1.2f", mAtkModifier), String.format("%1.2f", lvlModifier), String.format("%1.2f", rate));
		
		return (Rnd.get(100) < rate);
	}
	
	public static boolean calcMagicSuccess(Creature attacker, Creature target, L2Skill skill)
	{
		int lvlDifference = target.getStatus().getLevel() - ((skill.getMagicLevel() > 0 ? skill.getMagicLevel() : attacker.getStatus().getLevel()) + skill.getLevelDepend());
		double rate = 100;
		
		if (lvlDifference > 0)
			rate = (Math.pow(1.166, lvlDifference)) * 100;
		
		if (attacker instanceof Player && ((Player) attacker).getExpertiseWeaponPenalty())
			rate += 6000;
		
		if (Config.DEVELOPER)
			LOGGER.info("calcMagicSuccess(): name:{} lvlDiff:{} fail:{}%.", skill.getName(), lvlDifference, String.format("%1.2f", rate / 100));
		
		rate = Math.min(rate, 9900);
		
		return (Rnd.get(10000) > rate);
	}
	
	public static double calcManaDam(Creature attacker, Creature target, L2Skill skill, boolean ss, boolean bss)
	{
		double mAtk = attacker.getStatus().getMAtk(target, skill);
		double mDef = target.getStatus().getMDef(attacker, skill);
		double mp = target.getStatus().getMaxMp();
		
		if (bss)
			mAtk *= 4;
		else if (ss)
			mAtk *= 2;
		
		double damage = (Math.sqrt(mAtk) * skill.getPower(attacker) * (mp / 97)) / mDef;
		damage *= calcSkillVulnerability(attacker, target, skill, skill.getSkillType());
		return damage;
	}
	
	public static double calculateSkillResurrectRestorePercent(double baseRestorePercent, Creature caster)
	{
		if (baseRestorePercent == 0 || baseRestorePercent == 100)
			return baseRestorePercent;
		
		double restorePercent = baseRestorePercent * WIT_BONUS[caster.getStatus().getWIT()];
		if (restorePercent - baseRestorePercent > 20.0)
			restorePercent += 20.0;
		
		restorePercent = Math.max(restorePercent, baseRestorePercent);
		restorePercent = Math.min(restorePercent, 90.0);
		
		return restorePercent;
	}
	
	public static boolean calcPhysicalSkillEvasion(Creature target, L2Skill skill)
	{
		if (skill.isMagic())
			return false;
		
		return Rnd.get(100) < target.getStatus().calcStat(Stats.P_SKILL_EVASION, 0, null, skill);
	}
	
	public static boolean calcSkillMastery(Creature actor, L2Skill sk)
	{
		// Pointless check for Creature other than players, as initial value will stay 0.
		if (!(actor instanceof Player))
			return false;
		
		if (sk.getSkillType() == SkillType.FISHING)
			return false;
		
		double val = actor.getStatus().calcStat(Stats.SKILL_MASTERY, 0, null, null);
		
		if (((Player) actor).isMageClass())
			val *= INT_BONUS[actor.getStatus().getINT()];
		else
			val *= STR_BONUS[actor.getStatus().getSTR()];
		
		return Rnd.get(100) < val;
	}
	
	public static double calcValakasAttribute(Creature attacker, Creature target, L2Skill skill)
	{
		double calcPower = 0;
		double calcDefen = 0;
		
		if (skill != null && skill.getAttributeName().contains("valakas"))
		{
			calcPower = attacker.getStatus().calcStat(Stats.VALAKAS, calcPower, target, skill);
			calcDefen = target.getStatus().calcStat(Stats.VALAKAS_RES, calcDefen, target, skill);
		}
		else
		{
			calcPower = attacker.getStatus().calcStat(Stats.VALAKAS, calcPower, target, skill);
			if (calcPower > 0)
			{
				calcPower = attacker.getStatus().calcStat(Stats.VALAKAS, calcPower, target, skill);
				calcDefen = target.getStatus().calcStat(Stats.VALAKAS_RES, calcDefen, target, skill);
			}
		}
		return calcPower - calcDefen;
	}
	
	/**
	 * Calculate elemental modifier. There are 2 possible cases :
	 * <ul>
	 * <li>the check emanates from a skill : the result will be a multiplier, including an amount of attacker element, and the target vuln/prof.</li>
	 * <li>the check emanates from a weapon : the result is an addition of all elements, lowered/enhanced by the target vuln/prof</li>
	 * </ul>
	 * @param attacker : The attacker used to retrieve elemental attacks.
	 * @param target : The victim used to retrieve elemental protections.
	 * @param skill : If different of null, it will be considered as a skill resist check.
	 * @return A multiplier or a sum of damages.
	 */
	public static double calcElemental(Creature attacker, Creature target, L2Skill skill)
	{
		if (skill != null)
		{
			final ElementType element = skill.getElement();
			if (element != ElementType.NONE)
				return 1 + (((attacker.getStatus().getAttackElementValue(element) / 10.0) / 100.0) - (1 - target.getStatus().getDefenseElementValue(element)));
			
			return 1;
		}
		
		double elemDamage = 0;
		for (ElementType element : ElementType.VALUES)
		{
			if (element == ElementType.NONE)
				continue;
			
			final int attackerBonus = attacker.getStatus().getAttackElementValue(element);
			elemDamage += Math.max(0, (attackerBonus - (attackerBonus * (target.getStatus().getDefenseElementValue(element) / 100.0))));
		}
		return elemDamage;
	}
	
	/**
	 * Calculate skill reflection according to these three possibilities:
	 * <ul>
	 * <li>Reflect failed</li>
	 * <li>Normal reflect (just effects).</li>
	 * <li>Vengeance reflect (100% damage reflected but damage is also dealt to actor).</li>
	 * </ul>
	 * @param target : The skill's target.
	 * @param skill : The skill to test.
	 * @return SKILL_REFLECTED_FAILED, SKILL_REFLECT_SUCCEED or SKILL_REFLECT_VENGEANCE
	 */
	public static byte calcSkillReflect(Creature target, L2Skill skill)
	{
		// Some special skills (like hero debuffs...) or ignoring resistances skills can't be reflected.
		if (skill.ignoreResists() || !skill.canBeReflected())
			return SKILL_REFLECT_FAILED;
		
		// Only magic and melee skills can be reflected.
		if (!skill.isMagic() && (skill.getCastRange() == -1 || skill.getCastRange() > MELEE_ATTACK_RANGE))
			return SKILL_REFLECT_FAILED;
		
		byte reflect = SKILL_REFLECT_FAILED;
		
		// Check for non-reflected skilltypes, need additional retail check.
		switch (skill.getSkillType())
		{
			case BUFF:
			case REFLECT:
			case HEAL_PERCENT:
			case MANAHEAL_PERCENT:
			case HOT:
			case MPHOT:
			case AGGDEBUFF:
			case CONT:
				return SKILL_REFLECT_FAILED;
			
			case PDAM:
			case BLOW:
			case MDAM:
			case DEATHLINK:
			case CHARGEDAM:
				final double venganceChance = target.getStatus().calcStat((skill.isMagic()) ? Stats.VENGEANCE_SKILL_MAGIC_DAMAGE : Stats.VENGEANCE_SKILL_PHYSICAL_DAMAGE, 0, target, skill);
				if (venganceChance > Rnd.get(100))
					reflect |= SKILL_REFLECT_VENGEANCE;
				break;
		}
		
		final double reflectChance = target.getStatus().calcStat((skill.isMagic()) ? Stats.REFLECT_SKILL_MAGIC : Stats.REFLECT_SKILL_PHYSIC, 0, null, skill);
		if (Rnd.get(100) < reflectChance)
			reflect |= SKILL_REFLECT_SUCCEED;
		
		return reflect;
	}
	
	/**
	 * @param actor : The character affected.
	 * @param fallHeight : The height the NPC fallen.
	 * @return the damage, based on max HPs and falling height.
	 */
	public static double calcFallDam(Creature actor, int fallHeight)
	{
		if (!Config.ENABLE_FALLING_DAMAGE || fallHeight < 0)
			return 0;
		
		return actor.getStatus().calcStat(Stats.FALL, fallHeight * actor.getStatus().getMaxHp() / 1000, null, null);
	}
	
	/**
	 * @param type : The L2SkillType to test.
	 * @return true if the L2SkillType can affect a raid boss, false otherwise.
	 */
	public static boolean calcRaidAffected(SkillType type)
	{
		switch (type)
		{
			case MANADAM:
			case MDOT:
				return true;
			
			case CONFUSION:
			case ROOT:
			case STUN:
			case MUTE:
			case FEAR:
			case DEBUFF:
			case PARALYZE:
			case SLEEP:
			case AGGDEBUFF:
			case AGGREDUCE_CHAR:
				if (Rnd.get(1000) == 1)
					return true;
		}
		return false;
	}
	
	/**
	 * Calculates karma lost upon death.
	 * @param level : The level of the PKer.
	 * @param exp : The amount of xp earned.
	 * @return The amount of karma player has lost.
	 */
	public static int calculateKarmaLost(int level, long exp)
	{
		return (int) (exp / PlayerLevelData.getInstance().getPlayerLevel(level).getKarmaModifier() / 15);
	}
	
	/**
	 * Calculates karma gain upon player kill.
	 * @param pkCount : The current number of PK kills.
	 * @param isSummon : Does the victim is a summon or no (lesser karma gain if true).
	 * @return karma points that will be added to the player.
	 */
	public static int calculateKarmaGain(int pkCount, boolean isSummon)
	{
		int result = 14400;
		if (pkCount < 100)
			result = (int) (((((pkCount - 1) * 0.5) + 1) * 60) * 4);
		else if (pkCount < 180)
			result = (int) (((((pkCount + 1) * 0.125) + 37.5) * 60) * 4);
		
		if (isSummon)
			result = ((pkCount & 3) + result) >> 2;
		
		return result;
	}
}