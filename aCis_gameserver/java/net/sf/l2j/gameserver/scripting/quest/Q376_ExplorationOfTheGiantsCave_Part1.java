package net.sf.l2j.gameserver.scripting.quest;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.enums.QuestStatus;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.scripting.Quest;
import net.sf.l2j.gameserver.scripting.QuestState;

public class Q376_ExplorationOfTheGiantsCave_Part1 extends Quest
{
	private static final String qn = "Q376_ExplorationOfTheGiantsCave_Part1";
	
	// NPCs
	private static final int SOBLING = 31147;
	private static final int CLIFF = 30182;
	
	// Items
	private static final int PARCHMENT = 5944;
	private static final int DICTIONARY_BASIC = 5891;
	private static final int MYSTERIOUS_BOOK = 5890;
	private static final int DICTIONARY_INTERMEDIATE = 5892;
	private static final int[][] BOOKS =
	{
		// medical theory -> tallum tunic, tallum stockings
		{
			5937,
			5938,
			5939,
			5940,
			5941
		},
		// architecture -> dark crystal leather, tallum leather
		{
			5932,
			5933,
			5934,
			5935,
			5936
		},
		// golem plans -> dark crystal breastplate, tallum plate
		{
			5922,
			5923,
			5924,
			5925,
			5926
		},
		// basics of magic -> dark crystal gaiters, dark crystal leggings
		{
			5927,
			5928,
			5929,
			5930,
			5931
		}
	};
	
	// Rewards
	private static final int[][] RECIPES =
	{
		// medical theory -> tallum tunic, tallum stockings
		{
			5346,
			5354
		},
		// architecture -> dark crystal leather, tallum leather
		{
			5332,
			5334
		},
		// golem plans -> dark crystal breastplate, tallum plate
		{
			5416,
			5418
		},
		// basics of magic -> dark crystal gaiters, dark crystal leggings
		{
			5424,
			5340
		}
	};
	
	public Q376_ExplorationOfTheGiantsCave_Part1()
	{
		super(376, "Exploration of the Giants' Cave, Part 1");
		
		setItemsIds(DICTIONARY_BASIC, MYSTERIOUS_BOOK);
		
		addStartNpc(SOBLING);
		addTalkId(SOBLING, CLIFF);
		
		addKillId(20647, 20648, 20649, 20650);
	}
	
	@Override
	public String onAdvEvent(String event, Npc npc, Player player)
	{
		String htmltext = event;
		QuestState st = player.getQuestList().getQuestState(qn);
		if (st == null)
			return htmltext;
		
		// Sobling
		if (event.equalsIgnoreCase("31147-03.htm"))
		{
			st.setState(QuestStatus.STARTED);
			st.setCond(1);
			st.set("condBook", 1);
			playSound(player, SOUND_ACCEPT);
			giveItems(player, DICTIONARY_BASIC, 1);
		}
		else if (event.equalsIgnoreCase("31147-04.htm"))
		{
			htmltext = checkItems(player, st);
		}
		else if (event.equalsIgnoreCase("31147-09.htm"))
		{
			playSound(player, SOUND_FINISH);
			st.exitQuest(true);
		}
		// Cliff
		else if (event.equalsIgnoreCase("30182-02.htm"))
		{
			st.setCond(3);
			playSound(player, SOUND_MIDDLE);
			takeItems(player, MYSTERIOUS_BOOK, -1);
			giveItems(player, DICTIONARY_INTERMEDIATE, 1);
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
				htmltext = (player.getStatus().getLevel() < 51) ? "31147-01.htm" : "31147-02.htm";
				break;
			
			case STARTED:
				final int cond = st.getCond();
				switch (npc.getNpcId())
				{
					case SOBLING:
						htmltext = checkItems(player, st);
						break;
					
					case CLIFF:
						if (cond == 2 && player.getInventory().hasItems(MYSTERIOUS_BOOK))
							htmltext = "30182-01.htm";
						else if (cond == 3)
							htmltext = "30182-03.htm";
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
		
		// Drop parchment to anyone
		QuestState st = getRandomPartyMemberState(player, npc, QuestStatus.STARTED);
		if (st == null)
			return null;
		
		dropItems(st.getPlayer(), PARCHMENT, 1, 0, 20000);
		
		// Drop Mysterious Book to a party member, who still need it.
		st = getRandomPartyMember(player, npc, "condBook", "1");
		if (st != null && dropItems(st.getPlayer(), MYSTERIOUS_BOOK, 1, 1, 1000))
			st.unset("condBook");
		
		return null;
	}
	
	private static String checkItems(Player player, QuestState st)
	{
		if (player.getInventory().hasItems(MYSTERIOUS_BOOK))
		{
			if (st.getCond() == 1)
			{
				st.setCond(2);
				playSound(player, SOUND_MIDDLE);
				return "31147-07.htm";
			}
			return "31147-08.htm";
		}
		
		for (int type = 0; type < BOOKS.length; type++)
		{
			boolean complete = true;
			for (int book : BOOKS[type])
			{
				if (!player.getInventory().hasItems(book))
					complete = false;
			}
			
			if (complete)
			{
				for (int book : BOOKS[type])
					takeItems(player, book, 1);
				
				giveItems(player, Rnd.get(RECIPES[type]), 1);
				return "31147-04.htm";
			}
		}
		return "31147-05.htm";
	}
}