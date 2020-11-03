package net.sf.l2j.gameserver.model.actor.attack;

import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.item.kind.Weapon;
import net.sf.l2j.gameserver.network.SystemMessageId;

/**
 * This class groups all attack data related to a {@link Creature}.
 */
public class PlayerAttack extends PlayableAttack
{
	public PlayerAttack(Creature creature)
	{
		super(creature);
	}
	
	@Override
	public void doAttack(Creature target)
	{
		super.doAttack(target);
		
		((Player) _creature).clearRecentFakeDeath();
	}
	
	@Override
	public boolean canDoAttack(Creature target)
	{
		if (!super.canDoAttack(target))
			return false;
		
		final Player player = _creature.getActingPlayer();
		final Weapon weaponItem = player.getActiveWeaponItem();
		
		switch (weaponItem.getItemType())
		{
			case FISHINGROD:
				player.sendPacket(SystemMessageId.CANNOT_ATTACK_WITH_FISHING_POLE);
				return false;
			
			case BOW:
				if (!_creature.checkAndEquipArrows())
				{
					player.sendPacket(SystemMessageId.NOT_ENOUGH_ARROWS);
					return false;
				}
				
				final int mpConsume = weaponItem.getMpConsume();
				if (mpConsume > 0 && mpConsume > _creature.getCurrentMp())
				{
					player.sendPacket(SystemMessageId.NOT_ENOUGH_MP);
					return false;
				}
		}
		return true;
	}
}