package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.util.StringTokenizer;

import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;

/**
 * This class handles following admin commands:<br>
 * - test <custom parameter> = developer testing command
 */
public class AdminTest implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_test",
	};
	
	@Override
	public boolean useAdminCommand(String command, Player activeChar)
	{
		StringTokenizer st = new StringTokenizer(command);
		if (st.countTokens() > 1)
		{
			st.nextToken();
			switch (st.nextToken())
			{
				// Add your own cases.
				
				default:
					activeChar.sendMessage("Usage : //test ...");
					break;
			}
		}
		else
			activeChar.sendMessage("Usage : //test ...");
		
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}