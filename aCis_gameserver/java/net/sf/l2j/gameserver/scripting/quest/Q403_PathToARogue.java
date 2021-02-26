package net.sf.l2j.gameserver.scripting.quest;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.enums.Paperdoll;
import net.sf.l2j.gameserver.enums.QuestStatus;
import net.sf.l2j.gameserver.enums.actors.ClassId;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.serverpackets.SocialAction;
import net.sf.l2j.gameserver.scripting.Quest;
import net.sf.l2j.gameserver.scripting.QuestState;

public class Q403_PathToARogue extends Quest
{
	private static final String qn = "Q403_PathToARogue";
	
	// Items
	private static final int BEZIQUE_LETTER = 1180;
	private static final int NETI_BOW = 1181;
	private static final int NETI_DAGGER = 1182;
	private static final int SPARTOI_BONES = 1183;
	private static final int HORSESHOE_OF_LIGHT = 1184;
	private static final int MOST_WANTED_LIST = 1185;
	private static final int STOLEN_JEWELRY = 1186;
	private static final int STOLEN_TOMES = 1187;
	private static final int STOLEN_RING = 1188;
	private static final int STOLEN_NECKLACE = 1189;
	private static final int BEZIQUE_RECOMMENDATION = 1190;
	
	// NPCs
	private static final int BEZIQUE = 30379;
	private static final int NETI = 30425;
	
	public Q403_PathToARogue()
	{
		super(403, "Path to a Rogue");
		
		setItemsIds(BEZIQUE_LETTER, NETI_BOW, NETI_DAGGER, SPARTOI_BONES, HORSESHOE_OF_LIGHT, MOST_WANTED_LIST, STOLEN_JEWELRY, STOLEN_TOMES, STOLEN_RING, STOLEN_NECKLACE);
		
		addStartNpc(BEZIQUE);
		addTalkId(BEZIQUE, NETI);
		
		addKillId(20035, 20042, 20045, 20051, 20054, 20060, 27038);
	}
	
	@Override
	public String onAdvEvent(String event, Npc npc, Player player)
	{
		String htmltext = event;
		QuestState st = player.getQuestList().getQuestState(qn);
		if (st == null)
			return htmltext;
		
		if (event.equalsIgnoreCase("30379-05.htm"))
		{
			if (player.getClassId() != ClassId.HUMAN_FIGHTER)
				htmltext = (player.getClassId() == ClassId.ROGUE) ? "30379-02a.htm" : "30379-02.htm";
			else if (player.getStatus().getLevel() < 19)
				htmltext = "30379-02.htm";
			else if (player.getInventory().hasItems(BEZIQUE_RECOMMENDATION))
				htmltext = "30379-04.htm";
		}
		else if (event.equalsIgnoreCase("30379-06.htm"))
		{
			st.setState(QuestStatus.STARTED);
			st.setCond(1);
			playSound(player, SOUND_ACCEPT);
			giveItems(player, BEZIQUE_LETTER, 1);
		}
		else if (event.equalsIgnoreCase("30425-05.htm"))
		{
			st.setCond(2);
			playSound(player, SOUND_MIDDLE);
			giveItems(player, NETI_BOW, 1);
			giveItems(player, NETI_DAGGER, 1);
		}
		
		return htmltext;
	}
	
	@Override
	public String onTalk(Npc npc, Player player)
	{
		String htmltext = getNoQuestMsg();
		QuestState st = player.getQuestList().getQuestState(qn);
		if (st == null)
			return htmltext;
		
		switch (st.getState())
		{
			case CREATED:
				htmltext = "30379-01.htm";
				break;
			
			case STARTED:
				final int cond = st.getCond();
				switch (npc.getNpcId())
				{
					case BEZIQUE:
						if (cond == 1)
							htmltext = "30379-07.htm";
						else if (cond == 2 || cond == 3)
							htmltext = "30379-10.htm";
						else if (cond == 4)
						{
							htmltext = "30379-08.htm";
							st.setCond(5);
							playSound(player, SOUND_MIDDLE);
							takeItems(player, HORSESHOE_OF_LIGHT, 1);
							giveItems(player, MOST_WANTED_LIST, 1);
						}
						else if (cond == 5)
							htmltext = "30379-11.htm";
						else if (cond == 6)
						{
							htmltext = "30379-09.htm";
							takeItems(player, NETI_BOW, 1);
							takeItems(player, NETI_DAGGER, 1);
							takeItems(player, STOLEN_JEWELRY, 1);
							takeItems(player, STOLEN_NECKLACE, 1);
							takeItems(player, STOLEN_RING, 1);
							takeItems(player, STOLEN_TOMES, 1);
							giveItems(player, BEZIQUE_RECOMMENDATION, 1);
							rewardExpAndSp(player, 3200, 1500);
							player.broadcastPacket(new SocialAction(player, 3));
							playSound(player, SOUND_FINISH);
							st.exitQuest(true);
						}
						break;
					
					case NETI:
						if (cond == 1)
							htmltext = "30425-01.htm";
						else if (cond == 2)
							htmltext = "30425-06.htm";
						else if (cond == 3)
						{
							htmltext = "30425-07.htm";
							st.setCond(4);
							playSound(player, SOUND_MIDDLE);
							takeItems(player, SPARTOI_BONES, 10);
							giveItems(player, HORSESHOE_OF_LIGHT, 1);
						}
						else if (cond > 3)
							htmltext = "30425-08.htm";
						break;
				}
				break;
		}
		
		return htmltext;
	}
	
	@Override
	public String onKill(Npc npc, Creature killer)
	{
		final Player player = killer.getActingPlayer();
		
		final QuestState st = checkPlayerState(player, npc, QuestStatus.STARTED);
		if (st == null)
			return null;
		
		final int equippedItemId = player.getInventory().getItemIdFrom(Paperdoll.RHAND);
		if (equippedItemId != NETI_BOW && equippedItemId != NETI_DAGGER)
			return null;
		
		switch (npc.getNpcId())
		{
			case 20035:
			case 20045:
			case 20051:
				if (st.getCond() == 2 && dropItems(player, SPARTOI_BONES, 1, 10, 200000))
					st.setCond(3);
				break;
			
			case 20042:
				if (st.getCond() == 2 && dropItems(player, SPARTOI_BONES, 1, 10, 300000))
					st.setCond(3);
				break;
			
			case 20054:
			case 20060:
				if (st.getCond() == 2 && dropItems(player, SPARTOI_BONES, 1, 10, 800000))
					st.setCond(3);
				break;
			
			case 27038:
				if (st.getCond() == 5)
				{
					final int randomItem = Rnd.get(STOLEN_JEWELRY, STOLEN_NECKLACE);
					
					if (!player.getInventory().hasItems(randomItem))
					{
						giveItems(player, randomItem, 1);
						
						if (player.getInventory().hasItems(STOLEN_JEWELRY, STOLEN_TOMES, STOLEN_RING, STOLEN_NECKLACE))
						{
							st.setCond(6);
							playSound(player, SOUND_MIDDLE);
						}
						else
							playSound(player, SOUND_ITEMGET);
					}
				}
				break;
		}
		
		return null;
	}
}