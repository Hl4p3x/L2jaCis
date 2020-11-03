package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.math.MathUtil;
import net.sf.l2j.commons.pool.ConnectionPool;

import net.sf.l2j.gameserver.data.manager.CastleManager;
import net.sf.l2j.gameserver.data.manager.ClanHallManager;
import net.sf.l2j.gameserver.data.sql.PlayerInfoTable;
import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.data.xml.PlayerData;
import net.sf.l2j.gameserver.data.xml.PlayerLevelData;
import net.sf.l2j.gameserver.enums.actors.ClassId;
import net.sf.l2j.gameserver.enums.actors.Sex;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.PetDataEntry;
import net.sf.l2j.gameserver.model.PlayerLevel;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.actor.instance.Pet;
import net.sf.l2j.gameserver.model.group.Party;
import net.sf.l2j.gameserver.model.pledge.Clan;
import net.sf.l2j.gameserver.network.GameClient;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.AbstractNpcInfo.NpcInfo;
import net.sf.l2j.gameserver.network.serverpackets.GMViewItemList;
import net.sf.l2j.gameserver.network.serverpackets.HennaInfo;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class AdminEditChar implements IAdminCommandHandler
{
	private static final int PAGE_LIMIT = 20;
	
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_debug",
		"admin_show_characters",
		"admin_find_character",
		"admin_find_ip",
		"admin_find_account",
		"admin_find_dualbox",
		"admin_set",
		"admin_unsummon",
		"admin_summon_setlvl",
		"admin_show_pet_inv",
		"admin_fullfood",
		"admin_party_info",
		"admin_clan_info",
		"admin_remove_clan_penalty"
	};
	
	@Override
	public boolean useAdminCommand(String command, Player activeChar)
	{
		if (command.startsWith("admin_debug"))
		{
			try
			{
				final StringTokenizer st = new StringTokenizer(command, " ");
				st.nextToken();
				
				Player target = null;
				if (st.hasMoreTokens())
					target = World.getInstance().getPlayer(st.nextToken());
				
				if (target == null)
				{
					WorldObject object = activeChar.getTarget();
					if (object instanceof Player)
						target = (Player) object;
					else
						target = activeChar;
				}
				
				gatherPlayerInfo(activeChar, target);
			}
			catch (Exception e)
			{
				gatherPlayerInfo(activeChar, activeChar);
			}
		}
		else if (command.startsWith("admin_show_characters"))
		{
			try
			{
				listCharacters(activeChar, Integer.parseInt(command.substring(22)));
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //show_characters <page_number>");
			}
		}
		else if (command.startsWith("admin_find_character"))
		{
			try
			{
				findCharacter(activeChar, command.substring(21));
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //find_character <character_name>");
				listCharacters(activeChar, 1);
			}
		}
		else if (command.startsWith("admin_find_ip"))
		{
			try
			{
				findCharactersPerIp(activeChar, command.substring(14));
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //find_ip <www.xxx.yyy.zzz>");
				listCharacters(activeChar, 1);
			}
		}
		else if (command.startsWith("admin_find_account"))
		{
			try
			{
				findCharactersPerAccount(activeChar, command.substring(19));
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //find_account <player_name>");
				listCharacters(activeChar, 1);
			}
		}
		else if (command.startsWith("admin_find_dualbox"))
		{
			int multibox = 2;
			try
			{
				multibox = Integer.parseInt(command.substring(19));
				if (multibox < 1)
				{
					activeChar.sendMessage("Usage: //find_dualbox [number > 0]");
					return false;
				}
			}
			catch (Exception e)
			{
			}
			findDualbox(activeChar, multibox);
		}
		else if (command.startsWith("admin_set"))
		{
			final StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken();
			
			if (!st.hasMoreTokens())
			{
				activeChar.sendMessage("Usage: //set <access|class|color|exp|karma>");
				activeChar.sendMessage("Usage: //set <level|name|rec|sex|sp|tcolor|title>");
				return false;
			}
			
			WorldObject target = activeChar.getTarget();
			if (target == null)
				target = activeChar;
			
			switch (st.nextToken())
			{
				case "access":
					try
					{
						final int paramCount = st.countTokens();
						if (paramCount == 1)
						{
							final int lvl = Integer.parseInt(st.nextToken());
							if (target instanceof Player)
							{
								final Player player = (Player) target;
								player.setAccessLevel(lvl);
								
								if (lvl < 0)
									player.logout(false);
								
								activeChar.sendMessage(player.getName() + "'s access level is now set to " + lvl + ".");
							}
							else
								activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
						}
						else if (paramCount == 2)
						{
							final String name = st.nextToken();
							final int lvl = Integer.parseInt(st.nextToken());
							
							final Player player = World.getInstance().getPlayer(name);
							if (player != null)
							{
								player.setAccessLevel(lvl);
								
								if (lvl < 0)
									player.logout(false);
								
								activeChar.sendMessage(player.getName() + "'s access level is now set to " + lvl + ".");
							}
							else
							{
								try (Connection con = ConnectionPool.getConnection();
									PreparedStatement ps = con.prepareStatement("UPDATE characters SET accesslevel=? WHERE char_name=?"))
								{
									ps.setInt(1, lvl);
									ps.setString(2, name);
									ps.execute();
									
									final int count = ps.getUpdateCount();
									if (count == 0)
										activeChar.sendMessage(name + "couldn't be found - its access level is unaltered.");
									else
										activeChar.sendMessage(name + "'s access level is now set to " + lvl + ".");
								}
								catch (Exception e)
								{
								}
							}
						}
					}
					catch (Exception e)
					{
						activeChar.sendMessage("Usage: //set access <level> | <name> <level>");
					}
					break;
				
				case "class":
					try
					{
						if (!(target instanceof Player))
							return false;
						
						final int newClassId = Integer.parseInt(st.nextToken());
						if (newClassId < 0 || newClassId > ClassId.VALUES.length)
							return false;
						
						final ClassId newClass = ClassId.VALUES[newClassId];
						
						// Don't bother with dummy classes.
						if (newClass.getLevel() == -1)
						{
							activeChar.sendMessage("You tried to set an invalid class for " + target.getName() + ".");
							return false;
						}
						
						final Player player = (Player) target;
						
						// Don't bother edit ClassId if already set the same.
						if (player.getClassId() == newClass)
						{
							activeChar.sendMessage(target.getName() + " is already a(n) " + newClass.toString() + ".");
							return false;
						}
						
						player.setClassId(newClass.getId());
						if (!player.isSubClassActive())
							player.setBaseClass(newClass);
						
						player.refreshWeightPenalty();
						player.store();
						player.sendPacket(new HennaInfo(player));
						player.broadcastUserInfo();
						
						activeChar.sendMessage("You successfully set " + player.getName() + " class to " + newClass.toString() + ".");
					}
					catch (Exception e)
					{
						AdminHelpPage.showHelpPage(activeChar, "charclasses.htm");
					}
					break;
				
				case "color":
					try
					{
						if (!(target instanceof Player))
							return false;
						
						final Player player = (Player) target;
						player.getAppearance().setNameColor(Integer.decode("0x" + st.nextToken()));
						player.broadcastUserInfo();
						
						activeChar.sendMessage("You successfully set color name of " + player.getName() + ".");
					}
					catch (Exception e)
					{
						activeChar.sendMessage("Usage: //set color <number>");
					}
					break;
				
				case "exp":
					try
					{
						if (!(target instanceof Player))
							return false;
						
						final Player player = ((Player) target);
						
						final long newExp = Long.parseLong(st.nextToken());
						final long currentExp = player.getStatus().getExp();
						
						if (currentExp < newExp)
							player.addExpAndSp(newExp - currentExp, 0);
						else if (currentExp > newExp)
							player.removeExpAndSp(currentExp - newExp, 0);
						
						player.broadcastUserInfo();
						
						activeChar.sendMessage("You successfully set " + player.getName() + "'s XP to " + newExp + ".");
					}
					catch (Exception e)
					{
						activeChar.sendMessage("Usage: //set exp <number>");
					}
					break;
				
				case "karma":
					try
					{
						if (!(target instanceof Player))
							return false;
						
						final int newKarma = Integer.parseInt(st.nextToken());
						if (newKarma < 0)
						{
							activeChar.sendMessage("The karma value must be greater or equal to 0.");
							return false;
						}
						
						((Player) target).setKarma(newKarma);
						
						activeChar.sendMessage("You successfully set " + target.getName() + "'s karma to " + newKarma + ".");
					}
					catch (Exception e)
					{
						activeChar.sendMessage("Usage: //set karma <number>");
					}
					break;
				
				case "level":
					try
					{
						if (!(target instanceof Player))
							return false;
						
						final int newLevel = Integer.parseInt(st.nextToken());
						final PlayerLevel pl = PlayerLevelData.getInstance().getPlayerLevel(newLevel);
						if (pl == null)
						{
							activeChar.sendMessage("Invalid used level for //set level.");
							return false;
						}
						
						final long pXp = ((Player) target).getStatus().getExp();
						final long tXp = pl.getRequiredExpToLevelUp();
						
						if (pXp > tXp)
							((Player) target).removeExpAndSp(pXp - tXp, 0);
						else if (pXp < tXp)
							((Player) target).addExpAndSp(tXp - pXp, 0);
						
						activeChar.sendMessage("You successfully set " + target.getName() + "'s level to " + newLevel + ".");
					}
					catch (Exception e)
					{
						activeChar.sendMessage("Usage: //set level <number>");
					}
					break;
				
				case "name":
					try
					{
						final String newName = st.nextToken();
						
						if (target instanceof Player)
						{
							// Invalid pattern.
							if (!StringUtil.isValidString(newName, "^[A-Za-z0-9]{1,16}$"))
							{
								activeChar.sendPacket(SystemMessageId.INCORRECT_NAME_TRY_AGAIN);
								return false;
							}
							
							// Name is a npc name.
							if (NpcData.getInstance().getTemplateByName(newName) != null)
							{
								activeChar.sendPacket(SystemMessageId.INCORRECT_NAME_TRY_AGAIN);
								return false;
							}
							
							// Name already exists.
							if (PlayerInfoTable.getInstance().getPlayerObjectId(newName) > 0)
							{
								activeChar.sendPacket(SystemMessageId.INCORRECT_NAME_TRY_AGAIN);
								return false;
							}
							
							final Player player = (Player) target;
							player.setName(newName);
							PlayerInfoTable.getInstance().updatePlayerData(player, false);
							player.broadcastUserInfo();
							player.store();
							
							activeChar.sendMessage("You successfully set your target's name to " + player.getName() + ".");
						}
						else if (target instanceof Npc)
						{
							final Npc npc = (Npc) target;
							
							npc.setName(newName);
							npc.broadcastPacket(new NpcInfo(npc, null));
							
							activeChar.sendMessage("You successfully set your target's name to " + npc.getName() + ".");
						}
						else
							activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
					}
					catch (Exception e)
					{
						activeChar.sendMessage("Usage: //set name <name>");
					}
					break;
				
				case "rec":
					try
					{
						if (!(target instanceof Player))
							return false;
						
						final Player player = (Player) target;
						final int newRec = Integer.parseInt(st.nextToken());
						
						player.setRecomHave(newRec);
						player.broadcastUserInfo();
						
						activeChar.sendMessage("You successfully set " + player.getName() + " to " + newRec + ".");
					}
					catch (Exception e)
					{
						activeChar.sendMessage("Usage: //set rec <number>");
					}
					break;
				
				case "sex":
					try
					{
						if (!(target instanceof Player))
							return false;
						
						final Player player = (Player) target;
						final Sex sex = Enum.valueOf(Sex.class, st.nextToken().toUpperCase());
						
						if (sex == player.getAppearance().getSex())
						{
							activeChar.sendMessage(player.getName() + "'s sex is already defined as " + sex.toString() + ".");
							return false;
						}
						
						player.getAppearance().setSex(sex);
						player.broadcastUserInfo();
						player.decayMe();
						player.spawnMe();
						
						activeChar.sendMessage("You successfully set " + player.getName() + " gender to " + sex.toString() + ".");
					}
					catch (Exception e)
					{
						activeChar.sendMessage("Usage: //set sex <sex>");
					}
					break;
				
				case "sp":
					try
					{
						if (!(target instanceof Player))
							return false;
						
						final Player player = ((Player) target);
						
						final int newSp = Integer.parseInt(st.nextToken());
						final int currentSp = player.getStatus().getSp();
						
						if (currentSp < newSp)
							player.addExpAndSp(0, newSp - currentSp);
						else if (currentSp > newSp)
							player.removeExpAndSp(0, currentSp - newSp);
						
						player.broadcastUserInfo();
						
						activeChar.sendMessage("You successfully set " + player.getName() + "'s SP to " + newSp + ".");
					}
					catch (Exception e)
					{
						activeChar.sendMessage("Usage: //set sp <number>");
					}
					break;
				
				case "tcolor":
					try
					{
						if (!(target instanceof Player))
							return false;
						
						final Player player = (Player) target;
						player.getAppearance().setTitleColor(Integer.decode("0x" + command.substring(16)));
						player.broadcastUserInfo();
						
						activeChar.sendMessage("You successfully set title color name of " + player.getName() + ".");
					}
					catch (Exception e)
					{
						activeChar.sendMessage("Usage: //set tcolor <number>");
					}
					break;
				
				case "title":
					try
					{
						final String newTitle = st.nextToken();
						
						if (target instanceof Player)
						{
							final Player player = (Player) target;
							
							player.setTitle(newTitle);
							player.broadcastTitleInfo();
							
							activeChar.sendMessage("You successfully set your target's title to " + player.getTitle() + ".");
						}
						else if (target instanceof Npc)
						{
							final Npc npc = (Npc) target;
							
							npc.setTitle(newTitle);
							npc.broadcastPacket(new NpcInfo(npc, null));
							
							activeChar.sendMessage("You successfully set your target's title to " + npc.getTitle() + ".");
						}
						else
							activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
					}
					catch (Exception e)
					{
						activeChar.sendMessage("Usage: //set title <title>");
					}
					break;
				
				default:
					activeChar.sendMessage("Usage: //set <access|class|color|exp|karma>");
					activeChar.sendMessage("Usage: //set <level|name|rec|sex|sp|tcolor|title>");
					break;
			}
		}
		else if (command.startsWith("admin_unsummon"))
		{
			final WorldObject target = activeChar.getTarget();
			if (!(target instanceof Playable))
			{
				activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
				return false;
			}
			
			final Player targetPlayer = target.getActingPlayer();
			if (targetPlayer != null)
			{
				final Summon summon = targetPlayer.getSummon();
				if (summon != null)
					summon.unSummon(targetPlayer);
				else
					activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
			}
		}
		else if (command.startsWith("admin_summon_setlvl"))
		{
			final WorldObject target = activeChar.getTarget();
			if (!(target instanceof Pet))
			{
				activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
				return false;
			}
			
			final Pet pet = (Pet) target;
			try
			{
				final int level = Integer.parseInt(command.substring(20));
				
				final PetDataEntry pde = pet.getTemplate().getPetDataEntry(level);
				if (pde == null)
				{
					activeChar.sendMessage("Invalid level for //summon_setlvl.");
					return false;
				}
				
				final long oldExp = pet.getStatus().getExp();
				final long newExp = pde.getMaxExp();
				
				if (oldExp > newExp)
					pet.getStatus().removeExp(oldExp - newExp);
				else if (oldExp < newExp)
					pet.getStatus().addExp(newExp - oldExp);
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //summon_setlvl level");
			}
		}
		else if (command.startsWith("admin_show_pet_inv"))
		{
			WorldObject target;
			try
			{
				target = World.getInstance().getPet(Integer.parseInt(command.substring(19)));
			}
			catch (Exception e)
			{
				target = activeChar.getTarget();
			}
			
			if (target instanceof Pet)
				activeChar.sendPacket(new GMViewItemList((Pet) target));
			else
				activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
			
		}
		else if (command.startsWith("admin_fullfood"))
		{
			WorldObject target = activeChar.getTarget();
			if (target instanceof Pet)
			{
				Pet targetPet = (Pet) target;
				targetPet.setCurrentFed(targetPet.getPetData().getMaxMeal());
			}
			else
				activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
		}
		else if (command.startsWith("admin_party_info"))
		{
			WorldObject target;
			try
			{
				target = World.getInstance().getPlayer(command.substring(17));
				if (target == null)
					target = activeChar.getTarget();
			}
			catch (Exception e)
			{
				target = activeChar.getTarget();
			}
			
			if (!(target instanceof Player))
			{
				activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
				return false;
			}
			
			final Player player = ((Player) target);
			
			final Party party = player.getParty();
			if (party == null)
			{
				activeChar.sendMessage(player.getName() + " isn't in a party.");
				return false;
			}
			
			final StringBuilder sb = new StringBuilder(400);
			for (Player member : party.getMembers())
			{
				if (!party.isLeader(member))
					StringUtil.append(sb, "<tr><td width=150><a action=\"bypass -h admin_debug ", member.getName(), "\">", member.getName(), " (", member.getStatus().getLevel(), ")</a></td><td width=120 align=right>", member.getClassId().toString(), "</td></tr>");
				else
					StringUtil.append(sb, "<tr><td width=150><a action=\"bypass -h admin_debug ", member.getName(), "\"><font color=\"LEVEL\">", member.getName(), " (", member.getStatus().getLevel(), ")</font></a></td><td width=120 align=right>", member.getClassId().toString(), "</td></tr>");
			}
			
			final NpcHtmlMessage html = new NpcHtmlMessage(0);
			html.setFile("data/html/admin/partyinfo.htm");
			html.replace("%name%", player.getName());
			html.replace("%party%", sb.toString());
			activeChar.sendPacket(html);
		}
		else if (command.startsWith("admin_clan_info"))
		{
			try
			{
				final Player player = World.getInstance().getPlayer(command.substring(16));
				if (player == null)
				{
					activeChar.sendPacket(SystemMessageId.TARGET_CANT_FOUND);
					return false;
				}
				
				final Clan clan = player.getClan();
				if (clan == null)
				{
					activeChar.sendMessage("This player isn't in a clan.");
					return false;
				}
				
				final NpcHtmlMessage html = new NpcHtmlMessage(0);
				html.setFile("data/html/admin/claninfo.htm");
				html.replace("%name%", player.getName());
				html.replace("%clan_name%", clan.getName());
				html.replace("%clan_leader%", clan.getLeaderName());
				html.replace("%clan_level%", clan.getLevel());
				html.replace("%clan_has_castle%", (clan.hasCastle()) ? CastleManager.getInstance().getCastleById(clan.getCastleId()).getName() : "No");
				html.replace("%clan_has_clanhall%", (clan.hasClanHall()) ? ClanHallManager.getInstance().getClanHall(clan.getClanHallId()).getName() : "No");
				html.replace("%clan_points%", clan.getReputationScore());
				html.replace("%clan_players_count%", clan.getMembersCount());
				html.replace("%clan_ally%", (clan.getAllyId() > 0) ? clan.getAllyName() : "Not in ally");
				activeChar.sendPacket(html);
			}
			catch (Exception e)
			{
				activeChar.sendPacket(SystemMessageId.TARGET_CANT_FOUND);
			}
		}
		else if (command.startsWith("admin_remove_clan_penalty"))
		{
			try
			{
				StringTokenizer st = new StringTokenizer(command, " ");
				if (st.countTokens() != 3)
				{
					activeChar.sendMessage("Usage: //remove_clan_penalty join|create charname");
					return false;
				}
				
				st.nextToken();
				
				boolean changeCreateExpiryTime = st.nextToken().equalsIgnoreCase("create");
				String playerName = st.nextToken();
				
				Player player = World.getInstance().getPlayer(playerName);
				if (player == null)
				{
					try (Connection con = ConnectionPool.getConnection();
						PreparedStatement ps = con.prepareStatement("UPDATE characters SET " + (changeCreateExpiryTime ? "clan_create_expiry_time" : "clan_join_expiry_time") + " WHERE char_name=? LIMIT 1"))
					{
						ps.setString(1, playerName);
						ps.execute();
					}
					catch (Exception e)
					{
					}
				}
				else
				{
					// removing penalty
					if (changeCreateExpiryTime)
						player.setClanCreateExpiryTime(0);
					else
						player.setClanJoinExpiryTime(0);
				}
				activeChar.sendMessage("Clan penalty is successfully removed for " + playerName + ".");
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Couldn't remove clan penalty.");
			}
		}
		return true;
	}
	
	private static void listCharacters(Player activeChar, int page)
	{
		List<Player> players = new ArrayList<>(World.getInstance().getPlayers());
		
		final int max = MathUtil.countPagesNumber(players.size(), PAGE_LIMIT);
		
		players = players.subList((page - 1) * PAGE_LIMIT, Math.min(page * PAGE_LIMIT, players.size()));
		
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/admin/charlist.htm");
		
		final StringBuilder sb = new StringBuilder(players.size() * 200);
		
		// First use of sb.
		for (int x = 0; x < max; x++)
		{
			final int pagenr = x + 1;
			if (page == pagenr)
				StringUtil.append(sb, pagenr, "&nbsp;");
			else
				StringUtil.append(sb, "<a action=\"bypass -h admin_show_characters ", pagenr, "\">", pagenr, "</a>&nbsp;");
		}
		html.replace("%pages%", sb.toString());
		
		// Cleanup current sb.
		sb.setLength(0);
		
		// Second use of sb, add player info into new table row.
		for (Player player : players)
			StringUtil.append(sb, "<tr><td width=80><a action=\"bypass -h admin_debug ", player.getName(), "\">", player.getName(), "</a></td><td width=110>", player.getTemplate().getClassName(), "</td><td width=40>", player.getStatus().getLevel(), "</td></tr>");
		
		html.replace("%players%", sb.toString());
		activeChar.sendPacket(html);
	}
	
	/**
	 * Gather {@link Player} informations and send them on an existing {@link NpcHtmlMessage}. Additionally, set the target to this {@link Player}.
	 * @param activeChar : The {@link Player} who requested that action.
	 * @param player : The {@link Player} target to gather informations from.
	 * @param html : The {@link NpcHtmlMessage} used as reference.
	 */
	public static void gatherPlayerInfo(Player activeChar, Player player, NpcHtmlMessage html)
	{
		activeChar.setTarget(player);
		
		html.setFile("data/html/admin/charinfo.htm");
		html.replace("%name%", player.getName());
		html.replace("%level%", player.getStatus().getLevel());
		html.replace("%clan%", (player.getClan() != null) ? "<a action=\"bypass -h admin_clan_info " + player.getName() + "\">" + player.getClan().getName() + "</a>" : "none");
		html.replace("%xp%", player.getStatus().getExp());
		html.replace("%sp%", player.getStatus().getSp());
		html.replace("%class%", player.getTemplate().getClassName());
		html.replace("%baseclass%", PlayerData.getInstance().getClassNameById(player.getBaseClass()));
		html.replace("%currenthp%", (int) player.getStatus().getHp());
		html.replace("%maxhp%", player.getStatus().getMaxHp());
		html.replace("%karma%", player.getKarma());
		html.replace("%currentmp%", (int) player.getStatus().getMp());
		html.replace("%maxmp%", player.getStatus().getMaxMp());
		html.replace("%pvpflag%", player.getPvpFlag());
		html.replace("%currentcp%", (int) player.getStatus().getCp());
		html.replace("%maxcp%", player.getStatus().getMaxCp());
		html.replace("%pvpkills%", player.getPvpKills());
		html.replace("%pkkills%", player.getPkKills());
		html.replace("%patk%", player.getStatus().getPAtk(null));
		html.replace("%matk%", player.getStatus().getMAtk(null, null));
		html.replace("%pdef%", player.getStatus().getPDef(null));
		html.replace("%mdef%", player.getStatus().getMDef(null, null));
		html.replace("%accuracy%", player.getStatus().getAccuracy());
		html.replace("%evasion%", player.getStatus().getEvasionRate(null));
		html.replace("%critical%", player.getStatus().getCriticalHit(null, null));
		html.replace("%runspeed%", player.getStatus().getMoveSpeed());
		html.replace("%patkspd%", player.getStatus().getPAtkSpd());
		html.replace("%matkspd%", player.getStatus().getMAtkSpd());
		html.replace("%account%", player.getAccountName());
		html.replace("%ip%", (player.getClient().isDetached()) ? "Disconnected" : player.getClient().getConnection().getInetAddress().getHostAddress());
		html.replace("%prevai%", player.getAI().getPreviousIntention().getType().name());
		html.replace("%curai%", player.getAI().getCurrentIntention().getType().name());
		html.replace("%nextai%", player.getAI().getNextIntention().getType().name());
		html.replace("%loc%", player.getPosition().toString());
	}
	
	/**
	 * Gather {@link Player} informations and send them on a new {@link NpcHtmlMessage}. Additionally, set the target to this {@link Player}.
	 * @param activeChar : The {@link Player} who requested that action.
	 * @param player : The {@link Player} target to gather informations from.
	 */
	private static void gatherPlayerInfo(Player activeChar, Player player)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		gatherPlayerInfo(activeChar, player, html);
		activeChar.sendPacket(html);
	}
	
	/**
	 * Find the character based on his name, and send back the result to activeChar.
	 * @param activeChar The player to send back results.
	 * @param characterToFind The name to search.
	 */
	private static void findCharacter(Player activeChar, String characterToFind)
	{
		int charactersFound = 0;
		
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/admin/charfind.htm");
		
		final StringBuilder sb = new StringBuilder();
		
		// First use of sb, add player info into new Table row
		for (Player player : World.getInstance().getPlayers())
		{
			String name = player.getName();
			if (name.toLowerCase().contains(characterToFind.toLowerCase()))
			{
				charactersFound++;
				StringUtil.append(sb, "<tr><td width=80><a action=\"bypass -h admin_debug ", name, "\">", name, "</a></td><td width=110>", player.getTemplate().getClassName(), "</td><td width=40>", player.getStatus().getLevel(), "</td></tr>");
			}
			
			if (charactersFound > 20)
				break;
		}
		html.replace("%results%", sb.toString());
		
		// Cleanup sb.
		sb.setLength(0);
		
		// Second use of sb.
		if (charactersFound == 0)
			sb.append("s. Please try again.");
		else if (charactersFound > 20)
		{
			html.replace("%number%", " more than 20.");
			sb.append("s.<br>Please refine your search to see all of the results.");
		}
		else if (charactersFound == 1)
			sb.append(".");
		else
			sb.append("s.");
		
		html.replace("%number%", charactersFound);
		html.replace("%end%", sb.toString());
		activeChar.sendPacket(html);
	}
	
	/**
	 * @param activeChar
	 * @param IpAdress
	 * @throws IllegalArgumentException
	 */
	private static void findCharactersPerIp(Player activeChar, String IpAdress) throws IllegalArgumentException
	{
		boolean findDisconnected = false;
		
		if (IpAdress.equals("disconnected"))
			findDisconnected = true;
		else
		{
			if (!IpAdress.matches("^(?:(?:[0-9]|[1-9][0-9]|1[0-9][0-9]|2(?:[0-4][0-9]|5[0-5]))\\.){3}(?:[0-9]|[1-9][0-9]|1[0-9][0-9]|2(?:[0-4][0-9]|5[0-5]))$"))
				throw new IllegalArgumentException("Malformed IPv4 number");
		}
		
		int charactersFound = 0;
		String ip = "0.0.0.0";
		
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/admin/ipfind.htm");
		
		final StringBuilder sb = new StringBuilder(1000);
		for (Player player : World.getInstance().getPlayers())
		{
			GameClient client = player.getClient();
			if (client.isDetached())
			{
				if (!findDisconnected)
					continue;
			}
			else
			{
				if (findDisconnected)
					continue;
				
				ip = client.getConnection().getInetAddress().getHostAddress();
				if (!ip.equals(IpAdress))
					continue;
			}
			
			String name = player.getName();
			charactersFound++;
			StringUtil.append(sb, "<tr><td width=80><a action=\"bypass -h admin_debug ", name, "\">", name, "</a></td><td width=110>", player.getTemplate().getClassName(), "</td><td width=40>", player.getStatus().getLevel(), "</td></tr>");
			
			if (charactersFound > 20)
				break;
		}
		html.replace("%results%", sb.toString());
		
		final String replyMSG2;
		
		if (charactersFound == 0)
			replyMSG2 = ".";
		else if (charactersFound > 20)
		{
			html.replace("%number%", " more than 20.");
			replyMSG2 = "s.";
		}
		else if (charactersFound == 1)
			replyMSG2 = ".";
		else
			replyMSG2 = "s.";
		
		html.replace("%ip%", IpAdress);
		html.replace("%number%", charactersFound);
		html.replace("%end%", replyMSG2);
		activeChar.sendPacket(html);
	}
	
	/**
	 * Return accountinfo.htm with filled data related to {@link String} name set as parameter.
	 * @param activeChar : The {@link Player} calling this method.
	 * @param name : The {@link String} name to test.
	 */
	private static void findCharactersPerAccount(Player activeChar, String name)
	{
		final Player player = World.getInstance().getPlayer(name);
		if (player == null)
		{
			activeChar.sendPacket(SystemMessageId.TARGET_CANT_FOUND);
			return;
		}
		
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/admin/accountinfo.htm");
		html.replace("%name%", name);
		html.replace("%characters%", String.join("<br1>", player.getAccountChars().values()));
		html.replace("%account%", player.getAccountName());
		activeChar.sendPacket(html);
	}
	
	/**
	 * @param activeChar
	 * @param multibox
	 */
	private static void findDualbox(Player activeChar, int multibox)
	{
		Map<String, List<Player>> ipMap = new HashMap<>();
		
		String ip = "0.0.0.0";
		
		final Map<String, Integer> dualboxIPs = new HashMap<>();
		
		for (Player player : World.getInstance().getPlayers())
		{
			GameClient client = player.getClient();
			if (client == null || client.isDetached())
				continue;
			
			ip = client.getConnection().getInetAddress().getHostAddress();
			if (ipMap.get(ip) == null)
				ipMap.put(ip, new ArrayList<Player>());
			
			ipMap.get(ip).add(player);
			
			if (ipMap.get(ip).size() >= multibox)
			{
				Integer count = dualboxIPs.get(ip);
				if (count == null)
					dualboxIPs.put(ip, multibox);
				else
					dualboxIPs.put(ip, count++);
			}
		}
		
		List<String> keys = new ArrayList<>(dualboxIPs.keySet());
		Collections.sort(keys, new Comparator<String>()
		{
			@Override
			public int compare(String left, String right)
			{
				return dualboxIPs.get(left).compareTo(dualboxIPs.get(right));
			}
		});
		Collections.reverse(keys);
		
		final StringBuilder sb = new StringBuilder();
		for (String dualboxIP : keys)
			StringUtil.append(sb, "<a action=\"bypass -h admin_find_ip ", dualboxIP, "\">", dualboxIP, " (", dualboxIPs.get(dualboxIP), ")</a><br1>");
		
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/admin/dualbox.htm");
		html.replace("%multibox%", multibox);
		html.replace("%results%", sb.toString());
		html.replace("%strict%", "");
		activeChar.sendPacket(html);
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
