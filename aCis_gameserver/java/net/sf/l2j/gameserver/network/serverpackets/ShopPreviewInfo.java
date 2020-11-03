package net.sf.l2j.gameserver.network.serverpackets;

import java.util.Map;

import net.sf.l2j.gameserver.model.itemcontainer.Inventory;

public class ShopPreviewInfo extends L2GameServerPacket
{
	private final Map<Integer, Integer> _itemlist;
	
	public ShopPreviewInfo(Map<Integer, Integer> itemlist)
	{
		_itemlist = itemlist;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xf0);
		writeD(Inventory.PAPERDOLL_TOTALSLOTS);
		writeD(_itemlist.getOrDefault(Inventory.PAPERDOLL_REAR, 0)); // unverified
		writeD(_itemlist.getOrDefault(Inventory.PAPERDOLL_LEAR, 0)); // unverified
		writeD(_itemlist.getOrDefault(Inventory.PAPERDOLL_NECK, 0)); // unverified
		writeD(_itemlist.getOrDefault(Inventory.PAPERDOLL_RFINGER, 0)); // unverified
		writeD(_itemlist.getOrDefault(Inventory.PAPERDOLL_LFINGER, 0)); // unverified
		writeD(_itemlist.getOrDefault(Inventory.PAPERDOLL_HEAD, 0)); // unverified
		writeD(_itemlist.getOrDefault(Inventory.PAPERDOLL_RHAND, 0)); // good
		writeD(_itemlist.getOrDefault(Inventory.PAPERDOLL_LHAND, 0)); // good
		writeD(_itemlist.getOrDefault(Inventory.PAPERDOLL_GLOVES, 0)); // good
		writeD(_itemlist.getOrDefault(Inventory.PAPERDOLL_CHEST, 0)); // good
		writeD(_itemlist.getOrDefault(Inventory.PAPERDOLL_LEGS, 0)); // good
		writeD(_itemlist.getOrDefault(Inventory.PAPERDOLL_FEET, 0)); // good
		writeD(_itemlist.getOrDefault(Inventory.PAPERDOLL_BACK, 0)); // unverified
		writeD(_itemlist.getOrDefault(Inventory.PAPERDOLL_FACE, 0)); // unverified
		writeD(_itemlist.getOrDefault(Inventory.PAPERDOLL_HAIR, 0)); // unverified
		writeD(_itemlist.getOrDefault(Inventory.PAPERDOLL_HAIRALL, 0)); // unverified
		writeD(_itemlist.getOrDefault(Inventory.PAPERDOLL_UNDER, 0)); // unverified
	}
}