package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.math.MathUtil;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.SkillTable;
import net.sf.l2j.gameserver.data.cache.CrestCache;
import net.sf.l2j.gameserver.data.cache.HtmCache;
import net.sf.l2j.gameserver.data.manager.CursedWeaponManager;
import net.sf.l2j.gameserver.data.manager.ZoneManager;
import net.sf.l2j.gameserver.data.xml.AdminData;
import net.sf.l2j.gameserver.data.xml.AnnouncementData;
import net.sf.l2j.gameserver.data.xml.DoorData;
import net.sf.l2j.gameserver.data.xml.InstantTeleportData;
import net.sf.l2j.gameserver.data.xml.ItemData;
import net.sf.l2j.gameserver.data.xml.MultisellData;
import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.data.xml.TeleportData;
import net.sf.l2j.gameserver.data.xml.WalkerRouteData;
import net.sf.l2j.gameserver.enums.ScriptEventType;
import net.sf.l2j.gameserver.enums.actors.NpcSkillType;
import net.sf.l2j.gameserver.enums.skills.ElementType;
import net.sf.l2j.gameserver.enums.skills.SkillType;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.actor.instance.Door;
import net.sf.l2j.gameserver.model.actor.instance.MercenaryManagerNpc;
import net.sf.l2j.gameserver.model.actor.instance.Merchant;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.model.actor.instance.Pet;
import net.sf.l2j.gameserver.model.actor.instance.StaticObject;
import net.sf.l2j.gameserver.model.item.DropData;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ExServerPrimitive;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.scripting.Quest;
import net.sf.l2j.gameserver.skills.L2Skill;

/**
 * This class handles following admin commands:
 * <ul>
 * <li>admin : the different admin menus.</li>
 * <li>gmlist : includes/excludes active character from /gmlist results.</li>
 * <li>kill : handles the kill command.</li>
 * <li>reload : reloads specified component.</li>
 * <li>show : toggles specific show options.</li>
 * </ul>
 */
public class AdminAdmin implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_admin",
		"admin_gmlist",
		"admin_info",
		"admin_kill",
		"admin_reload",
		"admin_show",
	};
	
	private static final int PAGE_LIMIT = 7;
	
	@Override
	public boolean useAdminCommand(String command, Player activeChar)
	{
		if (command.startsWith("admin_admin"))
			showMainPage(activeChar, command);
		else if (command.startsWith("admin_gmlist"))
			activeChar.sendMessage((AdminData.getInstance().showOrHideGm(activeChar)) ? "Removed from GMList." : "Registered into GMList.");
		else if (command.startsWith("admin_info"))
		{
			WorldObject target = activeChar.getTarget();
			if (target == null)
				target = activeChar;
			
			final NpcHtmlMessage html = new NpcHtmlMessage(0);
			if (target instanceof Door)
			{
				final Door door = (Door) target;
				html.setFile("data/html/admin/doorinfo.htm");
				html.replace("%name%", door.getName());
				html.replace("%objid%", door.getObjectId());
				html.replace("%doorid%", door.getTemplate().getId());
				html.replace("%doortype%", door.getTemplate().getType().toString());
				html.replace("%doorlvl%", door.getTemplate().getLevel());
				html.replace("%castle%", (door.getCastle() != null) ? door.getCastle().getName() : "none");
				html.replace("%clanhall%", (door.getClanHall() != null) ? door.getClanHall().getName() : "none");
				html.replace("%opentype%", door.getTemplate().getOpenType().toString());
				html.replace("%initial%", door.getTemplate().isOpened() ? "Opened" : "Closed");
				html.replace("%ot%", door.getTemplate().getOpenTime());
				html.replace("%ct%", door.getTemplate().getCloseTime());
				html.replace("%rt%", door.getTemplate().getRandomTime());
				html.replace("%controlid%", door.getTemplate().getTriggerId());
				html.replace("%hp%", (int) door.getStatus().getHp());
				html.replace("%hpmax%", door.getStatus().getMaxHp());
				html.replace("%hpratio%", door.getStatus().getUpgradeHpRatio());
				html.replace("%pdef%", door.getStatus().getPDef(null));
				html.replace("%mdef%", door.getStatus().getMDef(null, null));
				html.replace("%spawn%", door.getPosition().toString());
				html.replace("%height%", door.getTemplate().getCollisionHeight());
			}
			else if (target instanceof Npc)
			{
				final Npc npc = (Npc) target;
				
				final StringTokenizer st = new StringTokenizer(command, " ");
				st.nextToken();
				
				if (!st.hasMoreTokens())
					sendGeneralInfos(activeChar, npc, html);
				else
				{
					final String subCommand = st.nextToken();
					switch (subCommand)
					{
						case "ai":
							sendAiInfos(activeChar, npc, html);
							break;
						
						case "drop":
						case "spoil":
							try
							{
								final int page = (st.hasMoreTokens()) ? Integer.parseInt(st.nextToken()) : 1;
								
								sendDropInfos(activeChar, npc, html, page, subCommand.equalsIgnoreCase("drop"));
							}
							catch (Exception e)
							{
								sendDropInfos(activeChar, npc, html, 1, true);
							}
							break;
						
						case "skill":
							sendSkillInfos(activeChar, npc, html);
							break;
						
						case "spawn":
							sendSpawnInfos(activeChar, npc, html);
							break;
						
						case "stat":
							sendStatsInfos(activeChar, npc, html);
							break;
						
						default:
							sendGeneralInfos(activeChar, npc, html);
					}
				}
			}
			else if (target instanceof Player)
			{
				AdminEditChar.gatherPlayerInfo(activeChar, (Player) target, html);
			}
			else if (target instanceof Summon)
			{
				final Summon summon = (Summon) target;
				final Player owner = target.getActingPlayer();
				
				html.setFile("data/html/admin/petinfo.htm");
				html.replace("%name%", (target.getName() == null) ? "N/A" : target.getName());
				html.replace("%level%", summon.getStatus().getLevel());
				html.replace("%exp%", summon.getStatus().getExp());
				html.replace("%owner%", (owner == null) ? "N/A" : " <a action=\"bypass -h admin_debug " + owner.getName() + "\">" + owner.getName() + "</a>");
				html.replace("%class%", summon.getClass().getSimpleName());
				html.replace("%ai%", (summon.hasAI()) ? summon.getAI().getCurrentIntention().getType().name() : "NULL");
				html.replace("%hp%", (int) summon.getStatus().getHp() + "/" + summon.getStatus().getMaxHp());
				html.replace("%mp%", (int) summon.getStatus().getMp() + "/" + summon.getStatus().getMaxMp());
				html.replace("%karma%", summon.getKarma());
				html.replace("%undead%", (summon.isUndead()) ? "yes" : "no");
				
				if (target instanceof Pet)
				{
					final Pet pet = ((Pet) target);
					
					html.replace("%inv%", (owner == null) ? "N/A" : " <a action=\"bypass admin_show_pet_inv " + owner.getObjectId() + "\">view</a>");
					html.replace("%food%", pet.getCurrentFed() + "/" + pet.getPetData().getMaxMeal());
					html.replace("%load%", pet.getInventory().getTotalWeight() + "/" + pet.getWeightLimit());
				}
				else
				{
					html.replace("%inv%", "none");
					html.replace("%food%", "N/A");
					html.replace("%load%", "N/A");
				}
			}
			else if (target instanceof StaticObject)
			{
				final StaticObject staticObject = (StaticObject) target;
				html.setFile("data/html/admin/staticinfo.htm");
				html.replace("%x%", staticObject.getX());
				html.replace("%y%", staticObject.getY());
				html.replace("%z%", staticObject.getZ());
				html.replace("%objid%", staticObject.getObjectId());
				html.replace("%staticid%", staticObject.getStaticObjectId());
				html.replace("%class%", staticObject.getClass().getSimpleName());
			}
			activeChar.sendPacket(html);
		}
		else if (command.startsWith("admin_kill"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken(); // skip command
			
			if (!st.hasMoreTokens())
			{
				final WorldObject obj = activeChar.getTarget();
				if (!(obj instanceof Creature))
					activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
				else
					kill(activeChar, (Creature) obj);
				
				return true;
			}
			
			String firstParam = st.nextToken();
			Player player = World.getInstance().getPlayer(firstParam);
			if (player != null)
			{
				if (st.hasMoreTokens())
				{
					String secondParam = st.nextToken();
					if (StringUtil.isDigit(secondParam))
					{
						int radius = Integer.parseInt(secondParam);
						for (Creature knownChar : player.getKnownTypeInRadius(Creature.class, radius))
						{
							if (knownChar.equals(activeChar))
								continue;
							
							kill(activeChar, knownChar);
						}
						activeChar.sendMessage("Killed all characters within a " + radius + " unit radius around " + player.getName() + ".");
					}
					else
						activeChar.sendMessage("Invalid radius.");
				}
				else
					kill(activeChar, player);
			}
			else if (StringUtil.isDigit(firstParam))
			{
				int radius = Integer.parseInt(firstParam);
				for (Creature knownChar : activeChar.getKnownTypeInRadius(Creature.class, radius))
					kill(activeChar, knownChar);
				
				activeChar.sendMessage("Killed all characters within a " + radius + " unit radius.");
			}
		}
		else if (command.startsWith("admin_reload"))
		{
			StringTokenizer st = new StringTokenizer(command);
			st.nextToken();
			try
			{
				do
				{
					String type = st.nextToken();
					if (type.startsWith("admin"))
					{
						AdminData.getInstance().reload();
						activeChar.sendMessage("Admin data has been reloaded.");
					}
					else if (type.startsWith("announcement"))
					{
						AnnouncementData.getInstance().reload();
						activeChar.sendMessage("The content of announcements.xml has been reloaded.");
					}
					else if (type.startsWith("config"))
					{
						Config.loadGameServer();
						activeChar.sendMessage("Configs files have been reloaded.");
					}
					else if (type.startsWith("crest"))
					{
						CrestCache.getInstance().reload();
						activeChar.sendMessage("Crests have been reloaded.");
					}
					else if (type.startsWith("cw"))
					{
						CursedWeaponManager.getInstance().reload();
						activeChar.sendMessage("Cursed weapons have been reloaded.");
					}
					else if (type.startsWith("door"))
					{
						DoorData.getInstance().reload();
						activeChar.sendMessage("Doors instance has been reloaded.");
					}
					else if (type.startsWith("htm"))
					{
						HtmCache.getInstance().reload();
						activeChar.sendMessage("The HTM cache has been reloaded.");
					}
					else if (type.startsWith("item"))
					{
						ItemData.getInstance().reload();
						activeChar.sendMessage("Items' templates have been reloaded.");
					}
					else if (type.equals("multisell"))
					{
						MultisellData.getInstance().reload();
						activeChar.sendMessage("The multisell instance has been reloaded.");
					}
					else if (type.equals("npc"))
					{
						NpcData.getInstance().reload();
						activeChar.sendMessage("NPCs templates have been reloaded.");
					}
					else if (type.startsWith("npcwalker"))
					{
						WalkerRouteData.getInstance().reload();
						activeChar.sendMessage("Walker routes have been reloaded.");
					}
					else if (type.startsWith("skill"))
					{
						SkillTable.getInstance().reload();
						activeChar.sendMessage("Skills' XMLs have been reloaded.");
					}
					else if (type.startsWith("teleport"))
					{
						InstantTeleportData.getInstance().reload();
						TeleportData.getInstance().reload();
						activeChar.sendMessage("Teleport locations have been reloaded.");
					}
					else if (type.startsWith("zone"))
					{
						ZoneManager.getInstance().reload();
						activeChar.sendMessage("Zones have been reloaded.");
					}
					else
					{
						activeChar.sendMessage("Usage : //reload <admin|announcement|config|crest|cw>");
						activeChar.sendMessage("Usage : //reload <door|htm|item|multisell|npc>");
						activeChar.sendMessage("Usage : //reload <npcwalker|skill|teleport|zone>");
					}
				}
				while (st.hasMoreTokens());
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage : //reload <admin|announcement|config|crest|cw>");
				activeChar.sendMessage("Usage : //reload <door|htm|item|multisell|npc>");
				activeChar.sendMessage("Usage : //reload <npcwalker|skill|teleport|zone>");
			}
		}
		else if (command.startsWith("admin_show"))
		{
			StringTokenizer st = new StringTokenizer(command);
			st.nextToken();
			
			try
			{
				switch (st.nextToken().toLowerCase())
				{
					case "clear":
						// Get target.
						WorldObject target = activeChar.getTarget();
						if (target == null)
							target = activeChar;
						
						if (target instanceof Player)
							((Player) target).clearDebugPackets();
						break;
					
					case "html":
						NpcHtmlMessage.SHOW_FILE = !NpcHtmlMessage.SHOW_FILE;
						break;
					
					case "move":
						// Get target.
						target = activeChar.getTarget();
						if (target == null)
							target = activeChar;
						
						if (target instanceof Creature)
						{
							// Toggle debug move.
							Creature c = (Creature) target;
							boolean move = !c.getMove().isDebugMove();
							c.getMove().setDebugMove(move);
							
							if (move)
							{
								// Send info messages.
								activeChar.sendMessage("Debug move enabled on " + c.getName());
								if (activeChar != c)
									c.sendMessage("Debug move was enabled.");
							}
							else
							{
								// Send info messages.
								activeChar.sendMessage("Debug move disabled on " + c.getName());
								if (activeChar != c)
									c.sendMessage("Debug move was disabled.");
								
								// Clear debug move packet to all GMs.
								World.getInstance().getPlayers().stream().filter(Player::isGM).forEach(p ->
								{
									final ExServerPrimitive debug = p.getDebugPacket("MOVE" + c.getObjectId());
									debug.reset();
									debug.sendTo(p);
								});
								
								// Clear debug move packet to self.
								if (c instanceof Player)
								{
									final ExServerPrimitive debug = ((Player) c).getDebugPacket("MOVE" + c.getObjectId());
									debug.reset();
									debug.sendTo((Player) c);
								}
							}
						}
						break;
					
					case "path":
						// Get target.
						target = activeChar.getTarget();
						if (target == null)
							target = activeChar;
						
						if (target instanceof Creature)
						{
							// Toggle debug move.
							Creature c = (Creature) target;
							boolean path = !c.getMove().isDebugPath();
							c.getMove().setDebugPath(path);
							
							if (path)
							{
								// Send info messages.
								activeChar.sendMessage("Debug path enabled on " + c.getName());
								if (activeChar != c)
									c.sendMessage("Debug path was enabled.");
							}
							else
							{
								// Send info messages.
								activeChar.sendMessage("Debug path disabled on " + c.getName());
								if (activeChar != c)
									c.sendMessage("Debug path was disabled.");
								
								// Clear debug move packet to all GMs.
								World.getInstance().getPlayers().stream().filter(Player::isGM).forEach(p ->
								{
									final ExServerPrimitive debug = p.getDebugPacket("PATH" + c.getObjectId());
									debug.reset();
									debug.sendTo(p);
								});
								
								// Clear debug move packet to self.
								if (c instanceof Player)
								{
									final ExServerPrimitive debug = ((Player) c).getDebugPacket("PATH" + c.getObjectId());
									debug.reset();
									debug.sendTo((Player) c);
								}
							}
						}
						break;
					
					default:
						activeChar.sendMessage("Usage : //show <clear|html|move|path>");
						break;
				}
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage : //show <clear|html|move|path>");
			}
		}
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	private static void kill(Player player, Creature target)
	{
		if (target instanceof Player)
		{
			if (!((Player) target).isGM())
				target.stopAllEffects(); // e.g. invincibility effect
			target.reduceCurrentHp(target.getStatus().getMaxHp() + target.getStatus().getMaxCp() + 1, player, null);
		}
		else
			target.reduceCurrentHp(target.getStatus().getMaxHp() + 1, player, null);
	}
	
	/**
	 * Send to the {@link Player} all <b>AI</b> informations regarding one {@link Npc}.
	 * @param player : The Player used as reference.
	 * @param npc : The Npc used as reference.
	 * @param html : The NpcHtmlMessage used as reference.
	 */
	private static void sendAiInfos(Player player, Npc npc, NpcHtmlMessage html)
	{
		html.setFile("data/html/admin/npcinfo/ai.htm");
		
		if (npc.hasAI())
		{
			html.replace("%ai%", npc.getAI().getClass().getSimpleName());
			html.replace("%prevai%", npc.getAI().getPreviousIntention().getType().name());
			html.replace("%curai%", npc.getAI().getCurrentIntention().getType().name());
			html.replace("%nextai%", npc.getAI().getNextIntention().getType().name());
		}
		else
		{
			html.replace("%prevai%", "");
			html.replace("%curai%", "");
			html.replace("%nextai%", "");
		}
		
		html.replace("%ai_type%", npc.getTemplate().getAiType().name());
		html.replace("%ai_clan%", (npc.getTemplate().getClans() != null) ? "<tr><td><font color=\"LEVEL\">Clan:</font></td><td>" + Arrays.toString(npc.getTemplate().getClans()) + " " + npc.getTemplate().getClanRange() + "</td></tr>" + ((npc.getTemplate().getIgnoredIds() != null) ? "<tr><td><font color=\"LEVEL\">Ignored ids:</font></td><td>" + Arrays.toString(npc.getTemplate().getIgnoredIds()) + "</td></tr>" : "") : "<tr><td>This NPC got no clan informations.</td></tr>");
		html.replace("%ai_move%", String.valueOf(npc.getTemplate().canMove()));
		html.replace("%ai_seed%", String.valueOf(npc.getTemplate().isSeedable()));
		html.replace("%ai_ssinfo%", npc.getCurrentSsCount() + "[" + npc.getTemplate().getSsCount() + "] - " + npc.getTemplate().getSsRate() + "%");
		html.replace("%ai_spsinfo%", npc.getCurrentSpsCount() + "[" + npc.getTemplate().getSpsCount() + "] - " + npc.getTemplate().getSpsRate() + "%");
		html.replace("%aggro%", npc.getTemplate().getAggroRange());
		html.replace("%enchant%", npc.getTemplate().getEnchantEffect());
		
		final StringBuilder sb = new StringBuilder(500);
		
		if (npc.getTemplate().getEventQuests().isEmpty())
			sb.append("This NPC isn't affected by scripts.");
		else
		{
			ScriptEventType type = null; // Used to see if we moved of type.
			
			// For any type of EventType
			for (Map.Entry<ScriptEventType, List<Quest>> entry : npc.getTemplate().getEventQuests().entrySet())
			{
				if (type != entry.getKey())
				{
					type = entry.getKey();
					StringUtil.append(sb, "<br><font color=\"LEVEL\">", type.name(), "</font><br1>");
				}
				
				for (Quest quest : entry.getValue())
					StringUtil.append(sb, quest.getName(), "<br1>");
			}
		}
		html.replace("%script%", sb.toString());
	}
	
	/**
	 * Send to the {@link Player} all <b>DROPS</b> or <b>SPOILS</b> informations regarding one {@link Npc}.
	 * @param player : The Player used as reference.
	 * @param npc : The Npc used as reference.
	 * @param html : The NpcHtmlMessage used as reference.
	 * @param page : The current page we are checking.
	 * @param isDrop : If true, we check drops only. If false, we check spoils.
	 */
	private static void sendDropInfos(Player player, Npc npc, NpcHtmlMessage html, int page, boolean isDrop)
	{
		List<DropData> list = (isDrop) ? npc.getTemplate().getAllDropData() : npc.getTemplate().getAllSpoilData();
		
		// Load static Htm.
		html.setFile("data/html/admin/npcinfo/drop.htm");
		
		if (list.isEmpty())
		{
			html.replace("%drop%", "<tr><td>This NPC has no " + ((isDrop) ? "drops" : "spoils") + ".</td></tr>");
			return;
		}
		
		final int max = MathUtil.countPagesNumber(list.size(), PAGE_LIMIT);
		if (page > max)
			page = max;
		
		list = list.subList((page - 1) * PAGE_LIMIT, Math.min(page * PAGE_LIMIT, list.size()));
		
		final StringBuilder sb = new StringBuilder(2000);
		
		int row = 0;
		for (DropData drop : list)
		{
			sb.append(((row % 2) == 0 ? "<table width=\"280\" bgcolor=\"000000\"><tr>" : "<table width=\"280\"><tr>"));
			
			final double chance = Math.min(100, (((drop.getItemId() == 57) ? drop.getChance() * Config.RATE_DROP_ADENA : drop.getChance() * Config.RATE_DROP_ITEMS) / 10000));
			final Item item = ItemData.getInstance().getTemplate(drop.getItemId());
			
			String name = item.getName();
			if (name.startsWith("Recipe: "))
				name = "R: " + name.substring(8);
			
			if (name.length() >= 45)
				name = name.substring(0, 42) + "...";
			
			StringUtil.append(sb, "<td width=34 height=34><img src=icon.noimage width=32 height=32></td>");
			StringUtil.append(sb, "<td width=246 height=34>", name, "<br1><font color=B09878>", ((isDrop) ? "Drop" : "Spoil"), ": ", chance, "% Min: ", drop.getMinDrop(), " Max: ", drop.getMaxDrop(), "</font></td>");
			
			sb.append("</tr></table><img src=\"L2UI.SquareGray\" width=277 height=1>");
			row++;
		}
		
		// Build page footer.
		sb.append("<br><img src=\"L2UI.SquareGray\" width=277 height=1><table width=\"100%\" bgcolor=000000><tr>");
		
		if (page > 1)
			StringUtil.append(sb, "<td align=left width=70><a action=\"bypass admin_info ", ((isDrop) ? "drop" : "spoil"), " ", page - 1, "\">Previous</a></td>");
		else
			StringUtil.append(sb, "<td align=left width=70>Previous</td>");
		
		StringUtil.append(sb, "<td align=center width=100>Page ", page, "</td>");
		
		if (page < max)
			StringUtil.append(sb, "<td align=right width=70><a action=\"bypass admin_info ", ((isDrop) ? "drop" : "spoil"), " ", page + 1, "\">Next</a></td>");
		else
			StringUtil.append(sb, "<td align=right width=70>Next</td>");
		
		sb.append("</tr></table><img src=\"L2UI.SquareGray\" width=277 height=1>");
		
		html.replace("%drop%", sb.toString());
	}
	
	/**
	 * Send to the {@link Player} all <b>GENERAL</b> informations regarding one {@link Npc}.
	 * @param player : The Player used as reference.
	 * @param npc : The Npc used as reference.
	 * @param html : The NpcHtmlMessage used as reference.
	 */
	public static void sendGeneralInfos(Player player, Npc npc, NpcHtmlMessage html)
	{
		html.setFile("data/html/admin/npcinfo/general.htm");
		
		html.replace("%class%", npc.getClass().getSimpleName());
		html.replace("%id%", npc.getTemplate().getNpcId());
		html.replace("%lvl%", npc.getTemplate().getLevel());
		html.replace("%name%", npc.getName());
		html.replace("%race%", npc.getTemplate().getRace().toString());
		html.replace("%tmplid%", npc.getTemplate().getIdTemplate());
		html.replace("%script%", npc.getScriptValue());
		html.replace("%castle%", (npc.getCastle() != null) ? npc.getCastle().getName() : "none");
		html.replace("%clanhall%", (npc.getClanHall() != null) ? npc.getClanHall().getName() : "none");
		html.replace("%siegablehall%", (npc.getSiegableHall() != null) ? npc.getSiegableHall().getName() : "none");
		html.replace("%shop%", ((npc instanceof Merchant || npc instanceof MercenaryManagerNpc) ? "<button value=\"Shop\" action=\"bypass -h admin_show_shop " + npc.getNpcId() + "\" width=65 height=19 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\">" : ""));
	}
	
	/**
	 * Send to the {@link Player} all <b>SPAWN</b> informations regarding one {@link Npc}.
	 * @param player : The Player used as reference.
	 * @param npc : The Npc used as reference.
	 * @param html : The NpcHtmlMessage used as reference.
	 */
	private static void sendSpawnInfos(Player player, Npc npc, NpcHtmlMessage html)
	{
		html.setFile("data/html/admin/npcinfo/spawn.htm");
		
		html.replace("%loc%", npc.getX() + " " + npc.getY() + " " + npc.getZ());
		html.replace("%dist%", (int) player.distance3D(npc));
		html.replace("%corpse%", StringUtil.getTimeStamp(npc.getTemplate().getCorpseTime()));
		
		if (npc.getSpawn() != null)
		{
			html.replace("%spawn%", npc.getSpawn().getLoc().toString());
			html.replace("%loc2d%", (int) npc.distance2D(npc.getSpawn().getLoc()));
			html.replace("%loc3d%", (int) npc.distance3D(npc.getSpawn().getLoc()));
			html.replace("%resp%", StringUtil.getTimeStamp(npc.getSpawn().getRespawnDelay()));
			html.replace("%rand_resp%", StringUtil.getTimeStamp(npc.getSpawn().getRespawnRandom()));
		}
		else
		{
			html.replace("%spawn%", "<font color=FF0000>null</font>");
			html.replace("%loc2d%", "<font color=FF0000>--</font>");
			html.replace("%loc3d%", "<font color=FF0000>--</font>");
			html.replace("%resp%", "<font color=FF0000>--</font>");
			html.replace("%rand_resp%", "<font color=FF0000>--</font>");
		}
		
		final StringBuilder sb = new StringBuilder(500);
		
		if (npc instanceof Monster)
		{
			final Monster monster = (Monster) npc;
			
			// Monster is a minion, deliver boss state.
			final Monster master = monster.getMaster();
			if (master != null)
			{
				html.replace("%type%", "minion");
				StringUtil.append(sb, "<tr><td><font color=", ((master.isDead()) ? "FF4040>" : "6161FF>"), master.toString(), "</td></tr>");
			}
			// Monster is a master, find back minions informations.
			else if (monster.hasMinions())
			{
				html.replace("%type%", "master");
				
				for (Entry<Monster, Boolean> data : monster.getMinionList().getMinions().entrySet())
					StringUtil.append(sb, "<tr><td><font color=", ((data.getValue()) ? "6161FF>" : "FF4040>"), data.getKey().toString(), "</td></tr>");
			}
			// Monster isn't anything.
			else
				html.replace("%type%", "regular monster");
		}
		else
			html.replace("%type%", "regular NPC");
		
		html.replace("%minion%", sb.toString());
	}
	
	/**
	 * Send to the {@link Player} all <b>STATS</b> informations regarding one {@link Npc}.
	 * @param player : The Player used as reference.
	 * @param npc : The Npc used as reference.
	 * @param html : The NpcHtmlMessage used as reference.
	 */
	private static void sendStatsInfos(Player player, Npc npc, NpcHtmlMessage html)
	{
		html.setFile("data/html/admin/npcinfo/stat.htm");
		
		html.replace("%hp%", (int) npc.getStatus().getHp());
		html.replace("%hpmax%", npc.getStatus().getMaxHp());
		html.replace("%mp%", (int) npc.getStatus().getMp());
		html.replace("%mpmax%", npc.getStatus().getMaxMp());
		html.replace("%patk%", npc.getStatus().getPAtk(null));
		html.replace("%matk%", npc.getStatus().getMAtk(null, null));
		html.replace("%pdef%", npc.getStatus().getPDef(null));
		html.replace("%mdef%", npc.getStatus().getMDef(null, null));
		html.replace("%accu%", npc.getStatus().getAccuracy());
		html.replace("%evas%", npc.getStatus().getEvasionRate(null));
		html.replace("%crit%", npc.getStatus().getCriticalHit(null, null));
		html.replace("%rspd%", npc.getStatus().getMoveSpeed());
		html.replace("%aspd%", npc.getStatus().getPAtkSpd());
		html.replace("%cspd%", npc.getStatus().getMAtkSpd());
		html.replace("%str%", npc.getStatus().getSTR());
		html.replace("%dex%", npc.getStatus().getDEX());
		html.replace("%con%", npc.getStatus().getCON());
		html.replace("%int%", npc.getStatus().getINT());
		html.replace("%wit%", npc.getStatus().getWIT());
		html.replace("%men%", npc.getStatus().getMEN());
		html.replace("%ele_fire%", npc.getStatus().getDefenseElementValue(ElementType.FIRE));
		html.replace("%ele_water%", npc.getStatus().getDefenseElementValue(ElementType.WATER));
		html.replace("%ele_wind%", npc.getStatus().getDefenseElementValue(ElementType.WIND));
		html.replace("%ele_earth%", npc.getStatus().getDefenseElementValue(ElementType.EARTH));
		html.replace("%ele_holy%", npc.getStatus().getDefenseElementValue(ElementType.HOLY));
		html.replace("%ele_dark%", npc.getStatus().getDefenseElementValue(ElementType.DARK));
	}
	
	/**
	 * Send to the {@link Player} all <b>SKILLS</b> informations regarding one {@link Npc}.
	 * @param player : The Player used as reference.
	 * @param npc : The Npc used as reference.
	 * @param html : The NpcHtmlMessage used as reference.
	 */
	private static void sendSkillInfos(Player player, Npc npc, NpcHtmlMessage html)
	{
		html.setFile("data/html/admin/npcinfo/skill.htm");
		
		if (npc.getTemplate().getSkills().isEmpty())
		{
			html.replace("%skill%", "This NPC doesn't hold any skill.");
			return;
		}
		
		final StringBuilder sb = new StringBuilder(500);
		
		NpcSkillType type = null; // Used to see if we moved of type.
		
		// For any type of SkillType
		for (Map.Entry<NpcSkillType, List<L2Skill>> entry : npc.getTemplate().getSkills().entrySet())
		{
			if (type != entry.getKey())
			{
				type = entry.getKey();
				StringUtil.append(sb, "<br><font color=\"LEVEL\">", type.name(), "</font><br1>");
			}
			
			for (L2Skill skill : entry.getValue())
				StringUtil.append(sb, ((skill.getSkillType() == SkillType.NOTDONE) ? ("<font color=\"777777\">" + skill.getName() + "</font>") : skill.getName()), " [", skill.getId(), "-", skill.getLevel(), "]<br1>");
		}
		
		html.replace("%skill%", sb.toString());
	}
	
	private static void showMainPage(Player player, String command)
	{
		String filename = "main";
		
		final StringTokenizer st = new StringTokenizer(command);
		st.nextToken();
		
		if (st.hasMoreTokens())
		{
			final String parameter = st.nextToken();
			if (StringUtil.isDigit(parameter))
			{
				final int mode = Integer.parseInt(parameter);
				if (mode == 2)
					filename = "game";
				else if (mode == 3)
					filename = "effects";
				else if (mode == 4)
					filename = "server";
			}
			else if (HtmCache.getInstance().isLoadable("data/html/admin/" + parameter + "_menu.htm"))
				filename = parameter;
		}
		
		AdminHelpPage.showHelpPage(player, filename + "_menu.htm");
	}
}