package net.sf.l2j.gameserver.scripting.quest;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.data.SkillTable;
import net.sf.l2j.gameserver.enums.QuestStatus;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.model.actor.instance.Pet;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.scripting.Quest;
import net.sf.l2j.gameserver.scripting.QuestState;
import net.sf.l2j.gameserver.skills.L2Skill;

/**
 * iCond is an internal variable, used because cond isn't developped on that quest (only 3 states) :
 * <ul>
 * <li>1-3 leads initial mimyu behavior ;</li>
 * <li>used for leaves support as mask : 4, 8, 16 or 32 = 60 overall ;</li>
 * <li>63 becomes the "marker" to get back to mimyu (60 + 3), meaning you already hit the 4 trees ;</li>
 * <li>set to 100 if mimyu check is ok.</li>
 * </ul>
 */
public class Q421_LittleWingsBigAdventure extends Quest
{
	private static final String qn = "Q421_LittleWingsBigAdventure";
	
	// NPCs
	private static final int CRONOS = 30610;
	private static final int MIMYU = 30747;
	
	// Item
	private static final int FAIRY_LEAF = 4325;
	
	public Q421_LittleWingsBigAdventure()
	{
		super(421, "Little Wing's Big Adventure");
		
		setItemsIds(FAIRY_LEAF);
		
		addStartNpc(CRONOS);
		addTalkId(CRONOS, MIMYU);
		
		addAttackId(27185, 27186, 27187, 27188);
		addKillId(27185, 27186, 27187, 27188);
	}
	
	@Override
	public String onAdvEvent(String event, Npc npc, Player player)
	{
		String htmltext = event;
		QuestState st = player.getQuestList().getQuestState(qn);
		if (st == null)
			return htmltext;
		
		if (event.equalsIgnoreCase("30610-06.htm"))
		{
			if (player.getInventory().getItemCount(3500) + player.getInventory().getItemCount(3501) + player.getInventory().getItemCount(3502) == 1)
			{
				// Find the level of the flute.
				for (int i = 3500; i < 3503; i++)
				{
					final ItemInstance item = player.getInventory().getItemByItemId(i);
					if (item != null && item.getEnchantLevel() >= 55)
					{
						st.setState(QuestStatus.STARTED);
						st.setCond(1);
						st.set("iCond", 1);
						st.set("summonOid", item.getObjectId());
						playSound(player, SOUND_ACCEPT);
						return "30610-05.htm";
					}
				}
			}
			
			// Exit quest if you got more than one flute, or the flute level doesn't meat requirements.
			st.exitQuest(true);
		}
		else if (event.equalsIgnoreCase("30747-02.htm"))
		{
			final Summon summon = player.getSummon();
			if (summon != null)
				htmltext = (summon.getControlItemId() == st.getInteger("summonOid")) ? "30747-04.htm" : "30747-03.htm";
		}
		else if (event.equalsIgnoreCase("30747-05.htm"))
		{
			final Summon summon = player.getSummon();
			if (summon == null || summon.getControlItemId() != st.getInteger("summonOid"))
				htmltext = "30747-06.htm";
			else
			{
				st.setCond(2);
				st.set("iCond", 3);
				playSound(player, SOUND_MIDDLE);
				giveItems(player, FAIRY_LEAF, 4);
			}
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
				// Wrong level.
				if (player.getStatus().getLevel() < 45)
					htmltext = "30610-01.htm";
				// Got more than one flute, or none.
				else if (player.getInventory().getItemCount(3500) + player.getInventory().getItemCount(3501) + player.getInventory().getItemCount(3502) != 1)
					htmltext = "30610-02.htm";
				else
				{
					// Find the level of the hatchling.
					for (int i = 3500; i < 3503; i++)
					{
						final ItemInstance item = player.getInventory().getItemByItemId(i);
						if (item != null && item.getEnchantLevel() >= 55)
							return "30610-04.htm";
					}
					
					// Invalid level.
					htmltext = "30610-03.htm";
				}
				break;
			
			case STARTED:
				switch (npc.getNpcId())
				{
					case CRONOS:
						htmltext = "30610-07.htm";
						break;
					
					case MIMYU:
						final int id = st.getInteger("iCond");
						if (id == 1)
						{
							htmltext = "30747-01.htm";
							st.set("iCond", 2);
						}
						else if (id == 2)
						{
							final Summon summon = player.getSummon();
							htmltext = (summon != null) ? ((summon.getControlItemId() == st.getInteger("summonOid")) ? "30747-04.htm" : "30747-03.htm") : "30747-02.htm";
						}
						else if (id == 3) // Explanation is done, leaves are already given.
							htmltext = "30747-07.htm";
						else if (id > 3 && id < 63) // Did at least one tree, but didn't manage to make them all.
							htmltext = "30747-11.htm";
						else if (id == 63) // Did all trees, no more leaves.
						{
							final Summon summon = player.getSummon();
							if (summon == null)
								return "30747-12.htm";
							
							if (summon.getControlItemId() != st.getInteger("summonOid"))
								return "30747-14.htm";
							
							htmltext = "30747-13.htm";
							st.set("iCond", 100);
						}
						else if (id == 100) // Spoke with the Fairy.
						{
							final Summon summon = player.getSummon();
							if (summon != null && summon.getControlItemId() == st.getInteger("summonOid"))
								return "30747-15.htm";
							
							if (player.getInventory().getItemCount(3500) + player.getInventory().getItemCount(3501) + player.getInventory().getItemCount(3502) > 1)
								return "30747-17.htm";
							
							for (int i = 3500; i < 3503; i++)
							{
								final ItemInstance item = player.getInventory().getItemByItemId(i);
								if (item != null && item.getObjectId() == st.getInteger("summonOid"))
								{
									takeItems(player, i, 1);
									giveItems(player, i + 922, 1, item.getEnchantLevel()); // TODO rebuild entirely pet system in order enchant is given a fuck. Supposed to give an item lvl XX for a flute level XX.
									playSound(player, SOUND_FINISH);
									st.exitQuest(true);
									return "30747-16.htm";
								}
							}
							
							// Curse if the registered objectId is the wrong one (switch flutes).
							htmltext = "30747-18.htm";
							
							final L2Skill skill = SkillTable.getInstance().getInfo(4167, 1);
							if (skill != null && player.getFirstEffect(skill) == null)
								skill.getEffects(npc, player);
						}
						break;
				}
				break;
		}
		
		return htmltext;
	}
	
	@Override
	public String onAttack(Npc npc, Creature attacker, int damage, L2Skill skill)
	{
		// Minions scream no matter current quest state.
		if (((Monster) npc).hasMinions())
		{
			for (Monster ghost : ((Monster) npc).getMinionList().getSpawnedMinions())
			{
				if (!ghost.isDead() && Rnd.get(100) < 1)
					ghost.broadcastNpcSay("We must protect the fairy tree!");
			}
		}
		
		if (attacker instanceof Pet)
		{
			final Player player = attacker.getActingPlayer();
			
			// Condition required : 2.
			final QuestState st = checkPlayerCondition(player, npc, 2);
			if (st == null)
				return null;
			
			// A pet was the attacker, and the objectId is the good one - random luck is reached and you still have some leaves ; go further.
			if (((Pet) attacker).getControlItemId() == st.getInteger("summonOid") && Rnd.get(100) < 1 && player.getInventory().hasItems(FAIRY_LEAF))
			{
				final int idMask = (int) Math.pow(2, (npc.getNpcId() - 27182) - 1);
				final int iCond = st.getInteger("iCond");
				
				if ((iCond | idMask) != iCond)
				{
					st.set("iCond", iCond | idMask);
					
					npc.broadcastNpcSay("Give me a Fairy Leaf...!");
					takeItems(player, FAIRY_LEAF, 1);
					npc.broadcastNpcSay("Leave now, before you incur the wrath of the guardian ghost...");
					
					// Four leafs have been used ; update quest state.
					if (st.getInteger("iCond") == 63)
					{
						st.setCond(3);
						playSound(player, SOUND_MIDDLE);
					}
					else
						playSound(player, SOUND_ITEMGET);
				}
			}
		}
		return null;
	}
	
	@Override
	public String onKill(Npc npc, Creature killer)
	{
		// Tree curses the killer.
		if (Rnd.get(100) < 30)
		{
			final L2Skill skill = SkillTable.getInstance().getInfo(4243, 1);
			if (skill != null && killer.getFirstEffect(skill) == null)
				skill.getEffects(npc, killer);
		}
		
		// Spawn 20 ghosts, attacking the killer.
		for (int i = 0; i < 20; i++)
		{
			final Npc ghost = addSpawn(27189, npc, true, 300000, false);
			ghost.forceAttack(killer, 2000);
		}
		
		return null;
	}
}