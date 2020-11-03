package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.util.StringTokenizer;

import net.sf.l2j.gameserver.data.SkillTable;
import net.sf.l2j.gameserver.data.xml.ArmorSetData;
import net.sf.l2j.gameserver.enums.Paperdoll;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.item.ArmorSet;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.item.kind.Armor;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.model.item.kind.Weapon;
import net.sf.l2j.gameserver.network.serverpackets.InventoryUpdate;
import net.sf.l2j.gameserver.skills.L2Skill;

public class AdminEnchant implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_enchant"
	};
	
	@Override
	public boolean useAdminCommand(String command, Player activeChar)
	{
		final StringTokenizer st = new StringTokenizer(command, " ");
		st.nextToken();
		
		if (st.countTokens() == 2)
		{
			try
			{
				final Paperdoll slot = Paperdoll.getEnumByName(st.nextToken());
				if (slot == Paperdoll.NULL)
				{
					activeChar.sendMessage("Unknown paperdoll slot.");
					return false;
				}
				
				final int enchant = Integer.parseInt(st.nextToken());
				if (enchant < 0 || enchant > 65535)
				{
					activeChar.sendMessage("You must set the enchant level between 0 - 65535.");
					return false;
				}
				
				WorldObject target = activeChar.getTarget();
				if (!(target instanceof Player))
					target = activeChar;
				
				final Player player = (Player) target;
				
				final ItemInstance item = player.getInventory().getItemFrom(slot);
				if (item == null)
				{
					activeChar.sendMessage(player.getName() + " doesn't wear any item in " + slot + " slot.");
					return false;
				}
				
				final Item it = item.getItem();
				final int oldEnchant = item.getEnchantLevel();
				
				// Do nothing if both values are the same.
				if (oldEnchant == enchant)
				{
					activeChar.sendMessage(player.getName() + "'s " + it.getName() + " enchant is already set to " + enchant + ".");
					return false;
				}
				
				item.setEnchantLevel(enchant);
				item.updateDatabase();
				
				// If item is equipped, verify the skill obtention/drop (+4 duals, +6 armorset).
				if (item.isEquipped())
				{
					final int currentEnchant = item.getEnchantLevel();
					
					// Skill bestowed by +4 duals.
					if (it instanceof Weapon)
					{
						// Old enchant was >= 4 and new is lower : we drop the skill.
						if (oldEnchant >= 4 && currentEnchant < 4)
						{
							final L2Skill enchant4Skill = ((Weapon) it).getEnchant4Skill();
							if (enchant4Skill != null)
							{
								player.removeSkill(enchant4Skill.getId(), false);
								player.sendSkillList();
							}
						}
						// Old enchant was < 4 and new is 4 or more : we add the skill.
						else if (oldEnchant < 4 && currentEnchant >= 4)
						{
							final L2Skill enchant4Skill = ((Weapon) it).getEnchant4Skill();
							if (enchant4Skill != null)
							{
								player.addSkill(enchant4Skill, false);
								player.sendSkillList();
							}
						}
					}
					// Add skill bestowed by +6 armorset.
					else if (it instanceof Armor)
					{
						// Old enchant was >= 6 and new is lower : we drop the skill.
						if (oldEnchant >= 6 && currentEnchant < 6)
						{
							// Check if player is wearing a chest item.
							final int itemId = player.getInventory().getItemIdFrom(Paperdoll.CHEST);
							if (itemId > 0)
							{
								final ArmorSet armorSet = ArmorSetData.getInstance().getSet(itemId);
								if (armorSet != null)
								{
									final int skillId = armorSet.getEnchant6skillId();
									if (skillId > 0)
									{
										player.removeSkill(skillId, false);
										player.sendSkillList();
									}
								}
							}
						}
						// Old enchant was < 6 and new is 6 or more : we add the skill.
						else if (oldEnchant < 6 && currentEnchant >= 6)
						{
							// Check if player is wearing a chest item.
							final int itemId = player.getInventory().getItemIdFrom(Paperdoll.CHEST);
							if (itemId > 0)
							{
								final ArmorSet armorSet = ArmorSetData.getInstance().getSet(itemId);
								if (armorSet != null && armorSet.isEnchanted6(player)) // has all parts of set enchanted to 6 or more
								{
									final int skillId = armorSet.getEnchant6skillId();
									if (skillId > 0)
									{
										final L2Skill skill = SkillTable.getInstance().getInfo(skillId, 1);
										if (skill != null)
										{
											player.addSkill(skill, false);
											player.sendSkillList();
										}
									}
								}
							}
						}
					}
				}
				
				final InventoryUpdate iu = new InventoryUpdate();
				iu.addModifiedItem(item);
				player.sendPacket(iu);
				
				player.broadcastUserInfo();
				
				activeChar.sendMessage(player.getName() + "'s " + it.getName() + " enchant was modified from " + oldEnchant + " to " + enchant + ".");
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Please specify a new enchant value.");
			}
		}
		else
		{
			activeChar.sendMessage("Usage: //enchant [slot name] [enchant level]");
			activeChar.sendMessage("Slots: under|lear|rear|neck|lfinger|rfinger|head|rhand|lhand");
			activeChar.sendMessage("Slots: gloves|chest|legs|feet|cloak|face|hair|hairall");
		}
		
		AdminHelpPage.showHelpPage(activeChar, "main_menu.htm");
		
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}