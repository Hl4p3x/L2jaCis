package net.sf.l2j.gameserver.network.serverpackets;

public class L2FriendSay extends L2GameServerPacket
{
	private final String _receiver;
	private final String _sender;
	private final String _message;
	
	public L2FriendSay(String sender, String reciever, String message)
	{
		_receiver = reciever;
		_sender = sender;
		_message = message;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xfd);
		writeD(0); // ??
		writeS(_receiver);
		writeS(_sender);
		writeS(_message);
	}
}