package net.sf.l2j.gameserver.handler.itemhandlers;

import net.sf.l2j.gameserver.data.SkillTable;
import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.data.xml.SummonItemData;
import net.sf.l2j.gameserver.enums.IntentionType;
import net.sf.l2j.gameserver.handler.IItemHandler;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.ChristmasTree;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.holder.IntIntHolder;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.spawn.Spawn;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;

public class SummonItems implements IItemHandler
{
	@Override
	public void useItem(Playable playable, ItemInstance item, boolean forceUse)
	{
		if (!(playable instanceof Player))
			return;
		
		final Player player = (Player) playable;
		
		if (player.isSitting())
		{
			player.sendPacket(SystemMessageId.CANT_MOVE_SITTING);
			return;
		}
		
		if (player.isInObserverMode())
			return;
		
		if (player.isAllSkillsDisabled() || player.getCast().isCastingNow())
			return;
		
		final IntIntHolder sitem = SummonItemData.getInstance().getSummonItem(item.getItemId());
		
		if ((player.getSummon() != null || player.isMounted()) && sitem.getValue() > 0)
		{
			player.sendPacket(SystemMessageId.SUMMON_ONLY_ONE);
			return;
		}
		
		if (player.getAttack().isAttackingNow())
		{
			player.sendPacket(SystemMessageId.YOU_CANNOT_SUMMON_IN_COMBAT);
			return;
		}
		
		final int npcId = sitem.getId();
		if (npcId == 0)
			return;
		
		final NpcTemplate npcTemplate = NpcData.getInstance().getTemplate(npcId);
		if (npcTemplate == null)
			return;
		
		player.getMove().stop();
		
		switch (sitem.getValue())
		{
			case 0: // static summons (like Christmas tree)
				try
				{
					for (ChristmasTree ch : player.getKnownTypeInRadius(ChristmasTree.class, 1200))
					{
						if (npcTemplate.getNpcId() == ChristmasTree.SPECIAL_TREE_ID)
						{
							player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANNOT_SUMMON_S1_AGAIN).addCharName(ch));
							return;
						}
					}
					
					if (player.destroyItem("Summon", item.getObjectId(), 1, null, false))
					{
						final Spawn spawn = new Spawn(npcTemplate);
						spawn.setLoc(player.getPosition());
						spawn.setRespawnState(false);
						
						final Npc npc = spawn.doSpawn(true);
						npc.setTitle(player.getName());
						npc.setWalkOrRun(false); // broadcast info
					}
				}
				catch (Exception e)
				{
					player.sendPacket(SystemMessageId.TARGET_CANT_FOUND);
				}
				break;
			case 1: // summon pet through an item
				player.getAI().tryTo(IntentionType.CAST, new SkillUseHolder(player, player, SkillTable.getInstance().getInfo(2046, 1), false, false), null);
				player.sendPacket(SystemMessageId.SUMMON_A_PET);
				break;
			case 2: // wyvern
				player.mount(sitem.getId(), item.getObjectId());
				break;
		}
	}
}
