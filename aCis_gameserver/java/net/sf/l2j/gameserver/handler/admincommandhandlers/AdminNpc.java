package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.awt.Color;
import java.util.List;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import net.sf.l2j.commons.lang.StringUtil;

import net.sf.l2j.gameserver.data.manager.BuyListManager;
import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.data.xml.WalkerRouteData;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.Merchant;
import net.sf.l2j.gameserver.model.buylist.NpcBuyList;
import net.sf.l2j.gameserver.model.buylist.Product;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.location.WalkerLocation;
import net.sf.l2j.gameserver.network.serverpackets.ExServerPrimitive;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class AdminNpc implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_show_shop",
		"admin_show_shoplist",
		"admin_walker"
	};
	
	@Override
	public boolean useAdminCommand(String command, Player activeChar)
	{
		final StringTokenizer st = new StringTokenizer(command, " ");
		st.nextToken();
		
		if (command.startsWith("admin_show_shoplist"))
		{
			try
			{
				showShopList(activeChar, Integer.parseInt(st.nextToken()));
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //show_shoplist <list_id>");
			}
		}
		else if (command.startsWith("admin_show_shop"))
		{
			try
			{
				showShop(activeChar, Integer.parseInt(st.nextToken()));
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //show_shop <npc_id>");
			}
		}
		else if (command.startsWith("admin_walker"))
		{
			// More tokens, we try to check parameter.
			if (st.hasMoreTokens())
			{
				String param = st.nextToken();
				if (param.equals("clear"))
				{
					final ExServerPrimitive debug = activeChar.getDebugPacket("WALKER");
					debug.reset();
					debug.sendTo(activeChar);
				}
				else
				{
					try
					{
						final int npcId = Integer.parseInt(param);
						final List<WalkerLocation> route = WalkerRouteData.getInstance().getWalkerRoute(npcId);
						if (route == null)
						{
							activeChar.sendMessage("The npcId " + npcId + " isn't linked to any WalkerRoute.");
							return false;
						}
						
						final ExServerPrimitive debug = activeChar.getDebugPacket("WALKER");
						debug.reset();
						
						// Draw the path.
						for (int i = 0; i < route.size(); i++)
						{
							final int nextIndex = i + 1;
							debug.addLine("Segment #" + nextIndex, Color.YELLOW, true, route.get(i), (nextIndex == route.size()) ? route.get(0) : route.get(nextIndex));
						}
						
						debug.sendTo(activeChar);
					}
					catch (Exception e)
					{
						activeChar.sendMessage("Usage: //walker <npc_id>");
					}
				}
			}
			
			// Send HTM no matter what.
			sendWalkerInfos(activeChar);
		}
		
		return true;
	}
	
	private static void showShopList(Player activeChar, int listId)
	{
		final NpcBuyList buyList = BuyListManager.getInstance().getBuyList(listId);
		if (buyList == null)
		{
			activeChar.sendMessage("BuyList template is unknown for id: " + listId + ".");
			return;
		}
		
		final StringBuilder sb = new StringBuilder(500);
		StringUtil.append(sb, "<html><body><center><font color=\"LEVEL\">", NpcData.getInstance().getTemplate(buyList.getNpcId()).getName(), " (", buyList.getNpcId(), ") buylist id: ", buyList.getListId(), "</font></center><br><table width=\"100%\"><tr><td width=200>Item</td><td width=80>Price</td></tr>");
		
		for (Product product : buyList.getProducts())
			StringUtil.append(sb, "<tr><td>", product.getItem().getName(), "</td><td>", product.getPrice(), "</td></tr>");
		
		sb.append("</table></body></html>");
		
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setHtml(sb.toString());
		activeChar.sendPacket(html);
	}
	
	private static void showShop(Player activeChar, int npcId)
	{
		final List<NpcBuyList> buyLists = BuyListManager.getInstance().getBuyListsByNpcId(npcId);
		if (buyLists.isEmpty())
		{
			activeChar.sendMessage("No buyLists found for id: " + npcId + ".");
			return;
		}
		
		final StringBuilder sb = new StringBuilder(500);
		StringUtil.append(sb, "<html><title>Merchant Shop Lists</title><body>");
		
		if (activeChar.getTarget() instanceof Merchant)
		{
			Npc merchant = (Npc) activeChar.getTarget();
			int taxRate = merchant.getCastle().getTaxPercent();
			
			StringUtil.append(sb, "<center><font color=\"LEVEL\">", merchant.getName(), " (", npcId, ")</font></center><br>Tax rate: ", taxRate, "%");
		}
		
		StringUtil.append(sb, "<table width=\"100%\">");
		
		for (NpcBuyList buyList : buyLists)
			StringUtil.append(sb, "<tr><td><a action=\"bypass -h admin_show_shoplist ", buyList.getListId(), " 1\">Buylist id: ", buyList.getListId(), "</a></td></tr>");
		
		StringUtil.append(sb, "</table></body></html>");
		
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setHtml(sb.toString());
		activeChar.sendPacket(html);
	}
	
	private static void sendWalkerInfos(Player activeChar)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/admin/walker.htm");
		
		final StringBuilder sb = new StringBuilder(500);
		
		for (Entry<Integer, List<WalkerLocation>> entry : WalkerRouteData.getInstance().getWalkerRoutes().entrySet())
		{
			final Location initialLoc = entry.getValue().get(0);
			final String teleLoc = initialLoc.toString().replaceAll(",", "");
			
			StringUtil.append(sb, "<tr><td width=180>NpcId: ", entry.getKey(), " - Path size: ", entry.getValue().size(), "</td><td width=50><a action=\"bypass admin_move_to ", teleLoc, "\">Tele. To</a></td><td width=50 align=right><a action=\"bypass admin_walker ", entry.getKey(), "\">Show</a></td></tr>");
		}
		
		html.replace("%routes%", sb.toString());
		activeChar.sendPacket(html);
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}