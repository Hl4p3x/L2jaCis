package net.sf.l2j.gameserver.scripting.quests;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.enums.actors.ClassRace;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.scripting.Quest;
import net.sf.l2j.gameserver.scripting.QuestState;

public class Q293_TheHiddenVeins extends Quest
{
	private static final String qn = "Q293_TheHiddenVeins";
	
	// Items
	private static final int CHRYSOLITE_ORE = 1488;
	private static final int TORN_MAP_FRAGMENT = 1489;
	private static final int HIDDEN_VEIN_MAP = 1490;
	
	// NPCs
	private static final int FILAUR = 30535;
	private static final int CHINCHIRIN = 30539;
	
	// Mobs
	private static final int UTUKU_ORC = 20446;
	private static final int UTUKU_ARCHER = 20447;
	private static final int UTUKU_GRUNT = 20448;
	
	public Q293_TheHiddenVeins()
	{
		super(293, "The Hidden Veins");
		
		setItemsIds(CHRYSOLITE_ORE, TORN_MAP_FRAGMENT, HIDDEN_VEIN_MAP);
		
		addStartNpc(FILAUR);
		addTalkId(FILAUR, CHINCHIRIN);
		
		addKillId(UTUKU_ORC, UTUKU_ARCHER, UTUKU_GRUNT);
	}
	
	@Override
	public String onAdvEvent(String event, Npc npc, Player player)
	{
		String htmltext = event;
		QuestState st = player.getQuestState(qn);
		if (st == null)
			return htmltext;
		
		if (event.equalsIgnoreCase("30535-03.htm"))
		{
			st.setState(STATE_STARTED);
			st.set("cond", "1");
			st.playSound(QuestState.SOUND_ACCEPT);
		}
		else if (event.equalsIgnoreCase("30535-06.htm"))
		{
			st.playSound(QuestState.SOUND_FINISH);
			st.exitQuest(true);
		}
		else if (event.equalsIgnoreCase("30539-02.htm"))
		{
			if (st.getQuestItemsCount(TORN_MAP_FRAGMENT) >= 4)
			{
				htmltext = "30539-03.htm";
				st.playSound(QuestState.SOUND_ITEMGET);
				st.takeItems(TORN_MAP_FRAGMENT, 4);
				st.giveItems(HIDDEN_VEIN_MAP, 1);
			}
		}
		
		return htmltext;
	}
	
	@Override
	public String onTalk(Npc npc, Player player)
	{
		String htmltext = getNoQuestMsg();
		QuestState st = player.getQuestState(qn);
		if (st == null)
			return htmltext;
		
		switch (st.getState())
		{
			case STATE_CREATED:
				if (player.getRace() != ClassRace.DWARF)
					htmltext = "30535-00.htm";
				else if (player.getStatus().getLevel() < 6)
					htmltext = "30535-01.htm";
				else
					htmltext = "30535-02.htm";
				break;
			
			case STATE_STARTED:
				switch (npc.getNpcId())
				{
					case FILAUR:
						final int ores = st.getQuestItemsCount(CHRYSOLITE_ORE);
						final int maps = st.getQuestItemsCount(HIDDEN_VEIN_MAP);
						
						if (ores + maps == 0)
							htmltext = "30535-04.htm";
						else
						{
							htmltext = (maps > 0) ? ((ores > 0) ? "30535-09.htm" : "30535-08.htm") : "30535-05.htm";
							st.takeItems(CHRYSOLITE_ORE, -1);
							st.takeItems(HIDDEN_VEIN_MAP, -1);
							
							int reward = (ores * 5) + (maps * 500);
							if (ores >= 10)
								reward += 2000;
							
							st.rewardItems(57, reward);
							st.rewardNewbieShots(6000, 0);
						}
						break;
					
					case CHINCHIRIN:
						htmltext = "30539-01.htm";
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
		
		final QuestState st = checkPlayerState(player, npc, STATE_STARTED);
		if (st == null)
			return null;
		
		final int chance = Rnd.get(100);
		
		if (chance > 50)
			st.dropItemsAlways(CHRYSOLITE_ORE, 1, 0);
		else if (chance < 5)
			st.dropItemsAlways(TORN_MAP_FRAGMENT, 1, 0);
		
		return null;
	}
}