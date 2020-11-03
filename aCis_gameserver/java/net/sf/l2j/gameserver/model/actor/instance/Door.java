package net.sf.l2j.gameserver.model.actor.instance;

import net.sf.l2j.commons.concurrent.ThreadPool;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.data.manager.CastleManager;
import net.sf.l2j.gameserver.data.manager.ClanHallManager;
import net.sf.l2j.gameserver.data.xml.DoorData;
import net.sf.l2j.gameserver.enums.DoorType;
import net.sf.l2j.gameserver.enums.OpenType;
import net.sf.l2j.gameserver.enums.SiegeSide;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.geoengine.geodata.IGeoObject;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.actor.ai.type.CreatureAI;
import net.sf.l2j.gameserver.model.actor.ai.type.DoorAI;
import net.sf.l2j.gameserver.model.actor.stat.DoorStat;
import net.sf.l2j.gameserver.model.actor.status.DoorStatus;
import net.sf.l2j.gameserver.model.actor.template.DoorTemplate;
import net.sf.l2j.gameserver.model.clanhall.ClanHall;
import net.sf.l2j.gameserver.model.entity.Castle;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.item.kind.Weapon;
import net.sf.l2j.gameserver.model.pledge.Clan;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ConfirmDlg;
import net.sf.l2j.gameserver.network.serverpackets.DoorInfo;
import net.sf.l2j.gameserver.network.serverpackets.DoorStatusUpdate;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.skills.L2Skill;

public class Door extends Creature implements IGeoObject
{
	private final Castle _castle;
	private final ClanHall _clanHall;
	
	private boolean _open;
	
	public Door(int objectId, DoorTemplate template)
	{
		super(objectId, template);
		
		// assign door to a castle
		_castle = CastleManager.getInstance().getCastleById(template.getCastleId());
		if (_castle != null)
			_castle.getDoors().add(this);
		
		// assign door to a clan hall
		_clanHall = ClanHallManager.getInstance().getClanHall(template.getClanHallId());
		if (_clanHall != null)
			_clanHall.getDoors().add(this);
		
		// temporarily set opposite state to initial state (will be set correctly by onSpawn)
		_open = !getTemplate().isOpened();
		
		// set name
		setName(template.getName());
	}
	
	@Override
	public CreatureAI getAI()
	{
		CreatureAI ai = _ai;
		if (ai == null)
		{
			synchronized (this)
			{
				ai = _ai;
				if (ai == null)
					_ai = ai = new DoorAI(this);
			}
		}
		return ai;
	}
	
	@Override
	public void initCharStat()
	{
		setStat(new DoorStat(this));
	}
	
	@Override
	public final DoorStat getStat()
	{
		return (DoorStat) super.getStat();
	}
	
	@Override
	public void initCharStatus()
	{
		setStatus(new DoorStatus(this));
	}
	
	@Override
	public final DoorStatus getStatus()
	{
		return (DoorStatus) super.getStatus();
	}
	
	@Override
	public final DoorTemplate getTemplate()
	{
		return (DoorTemplate) super.getTemplate();
	}
	
	@Override
	public void addFuncsToNewCharacter()
	{
	}
	
	@Override
	public final int getLevel()
	{
		return getTemplate().getLevel();
	}
	
	@Override
	public void updateAbnormalEffect()
	{
	}
	
	@Override
	public ItemInstance getActiveWeaponInstance()
	{
		return null;
	}
	
	@Override
	public Weapon getActiveWeaponItem()
	{
		return null;
	}
	
	@Override
	public ItemInstance getSecondaryWeaponInstance()
	{
		return null;
	}
	
	@Override
	public Weapon getSecondaryWeaponItem()
	{
		return null;
	}
	
	@Override
	public boolean isAttackableBy(Creature attacker)
	{
		if (!super.isAttackableBy(attacker))
			return false;
		
		// Doors can't be attacked by NPCs
		if (!(attacker instanceof Playable))
			return false;
		
		if (attacker instanceof Summon)
		{
			final Summon summon = (Summon) attacker;
			if (summon.getNpcId() == SiegeSummon.SWOOP_CANNON_ID)
				return false;
		}
		
		// Attackable during siege by attacker only
		final boolean isCastle = (_castle != null && _castle.getSiege().isInProgress());
		if (isCastle)
		{
			if (!_castle.getSiege().checkSides(attacker.getActingPlayer().getClan(), SiegeSide.ATTACKER))
				return false;
		}
		
		return isCastle;
	}
	
	@Override
	public void onInteract(Player player)
	{
		// Clan memebers (with privs) of door associated with a clan hall get a pop-up window to open/close the said door
		if (player.getClan() != null && _clanHall != null && player.getClanId() == _clanHall.getOwnerId() && player.hasClanPrivileges(Clan.CP_CH_OPEN_DOOR))
		{
			player.setRequestedGate(this);
			player.sendPacket(new ConfirmDlg((!isOpened()) ? 1140 : 1141));
		}
	}
	
	@Override
	public void onAction(Creature target, boolean isCtrlPressed, boolean isShiftPressed)
	{
		final Player player = (Player) target;
		// GMs lack the normal shift + action behaviour of regular players
		if (player.isGM() && isShiftPressed)
		{
			final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			html.setFile("data/html/admin/doorinfo.htm");
			html.replace("%name%", getName());
			html.replace("%objid%", getObjectId());
			html.replace("%doorid%", getTemplate().getId());
			html.replace("%doortype%", getTemplate().getType().toString());
			html.replace("%doorlvl%", getTemplate().getLevel());
			html.replace("%castle%", _castle != null ? _castle.getName() : "none");
			html.replace("%clanhall%", _clanHall != null ? _clanHall.getName() : "none");
			html.replace("%opentype%", getTemplate().getOpenType().toString());
			html.replace("%initial%", getTemplate().isOpened() ? "Opened" : "Closed");
			html.replace("%ot%", getTemplate().getOpenTime());
			html.replace("%ct%", getTemplate().getCloseTime());
			html.replace("%rt%", getTemplate().getRandomTime());
			html.replace("%controlid%", getTemplate().getTriggerId());
			html.replace("%hp%", (int) getCurrentHp());
			html.replace("%hpmax%", getMaxHp());
			html.replace("%hpratio%", getStat().getUpgradeHpRatio());
			html.replace("%pdef%", getPDef(null));
			html.replace("%mdef%", getMDef(null, null));
			html.replace("%spawn%", getX() + " " + getY() + " " + getZ());
			html.replace("%height%", getTemplate().getCollisionHeight());
			player.sendPacket(html);
			
			if (player.getTarget() != this)
				player.setTarget(this);
			
			return;
		}
		
		super.onAction(target, isCtrlPressed, isShiftPressed);
	}
	
	@Override
	public void reduceCurrentHp(double damage, Creature attacker, boolean awake, boolean isDOT, L2Skill skill)
	{
		// HPs can only be reduced during castle sieges.
		if (!(_castle != null && _castle.getSiege().isInProgress()))
			return;
		
		// Only siege summons can damage walls and use skills on walls/doors.
		if (!(attacker instanceof SiegeSummon) && (getTemplate().getType() == DoorType.WALL || skill != null))
			return;
		
		super.reduceCurrentHp(damage, attacker, awake, isDOT, skill);
	}
	
	@Override
	public void reduceCurrentHpByDOT(double i, Creature attacker, L2Skill skill)
	{
		// Doors can't be damaged by DOTs.
	}
	
	@Override
	public void onSpawn()
	{
		changeState(getTemplate().isOpened(), false);
		
		super.onSpawn();
	}
	
	@Override
	public boolean doDie(Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		
		if (!_open)
			GeoEngine.getInstance().removeGeoObject(this);
		
		if (_castle != null && _castle.getSiege().isInProgress())
			_castle.getSiege().announceToPlayers(SystemMessage.getSystemMessage((getTemplate().getType() == DoorType.WALL) ? SystemMessageId.CASTLE_WALL_DAMAGED : SystemMessageId.CASTLE_GATE_BROKEN_DOWN), false);
		
		return true;
	}
	
	@Override
	public void doRevive()
	{
		_open = getTemplate().isOpened();
		
		if (!_open)
			GeoEngine.getInstance().addGeoObject(this);
		
		super.doRevive();
	}
	
	@Override
	public void broadcastStatusUpdate()
	{
		broadcastPacket(new DoorStatusUpdate(this));
	}
	
	@Override
	public void sendInfo(Player player)
	{
		player.sendPacket(new DoorInfo(player, this));
		player.sendPacket(new DoorStatusUpdate(this));
	}
	
	@Override
	public int getGeoX()
	{
		return getTemplate().getGeoX();
	}
	
	@Override
	public int getGeoY()
	{
		return getTemplate().getGeoY();
	}
	
	@Override
	public int getGeoZ()
	{
		return getTemplate().getGeoZ();
	}
	
	@Override
	public int getHeight()
	{
		return (int) getTemplate().getCollisionHeight();
	}
	
	@Override
	public byte[][] getObjectGeoData()
	{
		return getTemplate().getGeoData();
	}
	
	@Override
	public double getCollisionHeight()
	{
		return getTemplate().getCollisionHeight() / 2;
	}
	
	/**
	 * Returns the {@link Door} ID.
	 * @return int : Returns the ID.
	 */
	public final int getDoorId()
	{
		return getTemplate().getId();
	}
	
	/**
	 * Returns true, when {@link Door} is opened.
	 * @return boolean : True, when opened.
	 */
	public final boolean isOpened()
	{
		return _open;
	}
	
	/**
	 * Returns true, when {@link Door} can be unlocked and opened.
	 * @return boolean : True, when can be unlocked and opened.
	 */
	public final boolean isUnlockable()
	{
		return getTemplate().getOpenType() == OpenType.SKILL;
	}
	
	/**
	 * Returns the actual damage of the door.
	 * @return int : Door damage.
	 */
	public final int getDamage()
	{
		return Math.max(0, Math.min(6, 6 - (int) Math.ceil(getCurrentHp() / getMaxHp() * 6)));
	}
	
	/**
	 * Opens the {@link Door}.
	 */
	public final void openMe()
	{
		// open door using external action
		changeState(true, false);
	}
	
	/**
	 * Closes the {@link Door}.
	 */
	public final void closeMe()
	{
		// close door using external action
		changeState(false, false);
	}
	
	/**
	 * Open/closes the {@link Door}, triggers other {@link Door} and schedules automatic open/close task.
	 * @param open : Requested status change.
	 * @param triggered : The status change was triggered by other.
	 */
	public final void changeState(boolean open, boolean triggered)
	{
		// door is dead or already in requested state, return
		if (isDead() || _open == open)
			return;
		
		// change door state and broadcast change
		_open = open;
		if (open)
			GeoEngine.getInstance().removeGeoObject(this);
		else
			GeoEngine.getInstance().addGeoObject(this);
		
		broadcastStatusUpdate();
		
		// door controls another door
		int triggerId = getTemplate().getTriggerId();
		if (triggerId > 0)
		{
			// get door and trigger state change
			Door door = DoorData.getInstance().getDoor(triggerId);
			if (door != null)
				door.changeState(open, true);
		}
		
		// request is not triggered
		if (!triggered)
		{
			// calculate time for automatic state change
			int time = open ? getTemplate().getCloseTime() : getTemplate().getOpenTime();
			if (getTemplate().getRandomTime() > 0)
				time += Rnd.get(getTemplate().getRandomTime());
			
			// try to schedule automatic state change
			if (time > 0)
				ThreadPool.schedule(() -> changeState(!open, false), time * 1000);
		}
	}
	
	public final Castle getCastle()
	{
		return _castle;
	}
	
	public final ClanHall getClanHall()
	{
		return _clanHall;
	}
}