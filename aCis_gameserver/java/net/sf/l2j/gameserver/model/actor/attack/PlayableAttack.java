package net.sf.l2j.gameserver.model.actor.attack;

import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;

/**
 * This class groups all attack data related to a {@link Creature}.
 */
public class PlayableAttack extends CreatureAttack
{
	public PlayableAttack(Creature creature)
	{
		super(creature);
	}
	
	@Override
	public boolean canDoAttack(Creature target)
	{
		if (!super.canDoAttack(target))
			return false;
		
		if (target.isInsideZone(ZoneId.PEACE) && !_creature.getActingPlayer().getAccessLevel().allowPeaceAttack())
		{
			_creature.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANT_ATK_PEACEZONE));
			return false;
		}
		
		return true;
	}
}