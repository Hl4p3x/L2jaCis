package net.sf.l2j.gameserver.skills.l2skills;

import net.sf.l2j.commons.util.StatsSet;

import net.sf.l2j.gameserver.data.manager.CastleManager;
import net.sf.l2j.gameserver.enums.SiegeSide;
import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.idfactory.IdFactory;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.SiegeFlag;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.entity.Siege;
import net.sf.l2j.gameserver.model.pledge.Clan;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.skills.L2Skill;

public class L2SkillSiegeFlag extends L2Skill
{
	private final boolean _isAdvanced;
	
	public L2SkillSiegeFlag(StatsSet set)
	{
		super(set);
		
		_isAdvanced = set.getBool("isAdvanced", false);
	}
	
	@Override
	public void useSkill(Creature activeChar, WorldObject[] targets)
	{
		if (!(activeChar instanceof Player))
			return;
		
		final Player player = activeChar.getActingPlayer();
		
		if (!check(player, true))
			return;
		
		final Clan clan = player.getClan();
		if (clan == null)
			return;
		
		// Template initialization
		final StatsSet npcDat = new StatsSet();
		
		npcDat.set("id", 35062);
		npcDat.set("type", "");
		
		npcDat.set("name", clan.getName());
		npcDat.set("usingServerSideName", true);
		
		npcDat.set("hp", (_isAdvanced) ? 100000 : 50000);
		npcDat.set("mp", 0);
		
		npcDat.set("radius", 10);
		npcDat.set("height", 80);
		
		npcDat.set("pAtk", 0);
		npcDat.set("mAtk", 0);
		npcDat.set("pDef", 500);
		npcDat.set("mDef", 500);
		
		npcDat.set("runSpd", 0); // Have to keep this, static object MUST BE 0 (critical error otherwise).
		
		// Spawn a new flag.
		final SiegeFlag flag = new SiegeFlag(clan, IdFactory.getInstance().getNextId(), new NpcTemplate(npcDat));
		flag.getStatus().setMaxHp();
		flag.spawnMe(player.getPosition());
	}
	
	/**
	 * @param player : The {@link Player} to test.
	 * @param isCheckOnly : If false, send a notification to the {@link Player} telling him why the operation failed.
	 * @return True if the {@link Player} can place a {@link SiegeFlag}.
	 */
	public static boolean check(Player player, boolean isCheckOnly)
	{
		final Siege siege = CastleManager.getInstance().getActiveSiege(player);
		
		SystemMessage sm = null;
		if (siege == null || !siege.checkSide(player.getClan(), SiegeSide.ATTACKER))
			sm = SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED).addSkillName(247);
		else if (!player.isClanLeader())
			sm = SystemMessage.getSystemMessage(SystemMessageId.ONLY_CLAN_LEADER_CAN_ISSUE_COMMANDS);
		else if (player.getClan().getFlag() != null)
			sm = SystemMessage.getSystemMessage(SystemMessageId.NOT_ANOTHER_HEADQUARTERS);
		else if (!player.isInsideZone(ZoneId.HQ))
			sm = SystemMessage.getSystemMessage(SystemMessageId.NOT_SET_UP_BASE_HERE);
		else if (!player.getKnownTypeInRadius(SiegeFlag.class, 400).isEmpty())
			sm = SystemMessage.getSystemMessage(SystemMessageId.HEADQUARTERS_TOO_CLOSE);
		
		if (sm != null && !isCheckOnly)
			player.sendPacket(sm);
		
		return sm == null;
	}
}