package net.sf.l2j.gameserver.network.clientpackets;

import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.container.player.BlockList;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.FriendAddRequest;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;

public final class RequestFriendInvite extends L2GameClientPacket
{
	private String _targetName;
	
	@Override
	protected void readImpl()
	{
		_targetName = readS();
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getClient().getPlayer();
		if (player == null)
			return;
		
		final Player target = World.getInstance().getPlayer(_targetName);
		
		if (target == null || !target.isOnline() || !target.getAppearance().isVisible())
		{
			player.sendPacket(SystemMessageId.THE_USER_YOU_REQUESTED_IS_NOT_IN_GAME);
			return;
		}
		
		if (target == player)
		{
			player.sendPacket(SystemMessageId.YOU_CANNOT_ADD_YOURSELF_TO_OWN_FRIEND_LIST);
			return;
		}
		
		if (BlockList.isBlocked(target, player))
		{
			player.sendPacket(SystemMessageId.FAILED_TO_INVITE_A_FRIEND);
			return;
		}
		
		if (player.getFriendList().contains(target.getObjectId()))
		{
			player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_ALREADY_IN_FRIENDS_LIST).addString(_targetName));
			return;
		}
		
		if (!target.isProcessingRequest())
		{
			player.onTransactionRequest(target);
			target.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_REQUESTED_TO_BECOME_FRIENDS).addCharName(player));
			target.sendPacket(new FriendAddRequest(player.getName()));
		}
		else
			player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_IS_BUSY_TRY_LATER).addString(_targetName));
	}
}