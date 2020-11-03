package net.sf.l2j.gameserver.network.clientpackets;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.enums.actors.OperateType;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.trade.BuyProcessItem;
import net.sf.l2j.gameserver.model.trade.TradeList;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.PrivateStoreManageListBuy;
import net.sf.l2j.gameserver.network.serverpackets.PrivateStoreMsgBuy;

public final class SetPrivateStoreListBuy extends L2GameClientPacket
{
	private static final int BATCH_LENGTH = 16;
	
	private BuyProcessItem[] _items = null;
	
	@Override
	protected void readImpl()
	{
		final int count = readD();
		if (count < 1 || count > Config.MAX_ITEM_IN_PACKET || count * BATCH_LENGTH != _buf.remaining())
			return;
		
		_items = new BuyProcessItem[count];
		
		for (int i = 0; i < count; i++)
		{
			final int itemId = readD();
			final int enchant = readH();
			readH();
			final int cnt = readD();
			final int price = readD();
			
			_items[i] = new BuyProcessItem(itemId, cnt, price, enchant);
		}
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getClient().getPlayer();
		if (player == null)
			return;
		
		// Integrity check.
		if (!player.getInventory().canPassBuyProcess(_items))
			return;
		
		final TradeList tradeList = player.getBuyList();
		tradeList.clear();
		
		if (!player.getAccessLevel().allowTransaction())
		{
			player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return;
		}
		
		// Check multiple conditions. Message is sent directly from the method.
		if (!player.canOpenPrivateStore(false))
			return;
		
		// Check maximum number of allowed slots for pvt shops
		if (_items.length > player.getPrivateBuyStoreLimit())
		{
			player.sendPacket(SystemMessageId.YOU_HAVE_EXCEEDED_QUANTITY_THAT_CAN_BE_INPUTTED);
			player.sendPacket(new PrivateStoreManageListBuy(player));
			return;
		}
		
		long totalCost = 0;
		
		for (BuyProcessItem i : _items)
		{
			if (!i.addToTradeList(tradeList))
			{
				player.sendPacket(SystemMessageId.EXCEEDED_THE_MAXIMUM);
				player.sendPacket(new PrivateStoreManageListBuy(player));
				return;
			}
			
			totalCost += i.getCost();
			if (totalCost > Integer.MAX_VALUE)
			{
				player.sendPacket(SystemMessageId.EXCEEDED_THE_MAXIMUM);
				player.sendPacket(new PrivateStoreManageListBuy(player));
				return;
			}
		}
		
		// Check for available funds
		if (totalCost > player.getAdena())
		{
			player.sendPacket(SystemMessageId.THE_PURCHASE_PRICE_IS_HIGHER_THAN_MONEY);
			player.sendPacket(new PrivateStoreManageListBuy(player));
			return;
		}
		
		player.getMove().stop();
		player.sitDown();
		player.setOperateType(OperateType.BUY);
		player.broadcastUserInfo();
		player.broadcastPacket(new PrivateStoreMsgBuy(player));
	}
}