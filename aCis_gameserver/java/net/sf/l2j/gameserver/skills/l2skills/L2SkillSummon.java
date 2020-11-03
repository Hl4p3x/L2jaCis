package net.sf.l2j.gameserver.skills.l2skills;

import net.sf.l2j.commons.util.StatsSet;

import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.idfactory.IdFactory;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.Servitor;
import net.sf.l2j.gameserver.model.actor.instance.SiegeSummon;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.skills.L2Skill;

public class L2SkillSummon extends L2Skill
{
	public static final int SKILL_CUBIC_MASTERY = 143;
	
	private final int _npcId;
	private final float _expPenalty;
	private final boolean _isCubic;
	
	private final int _activationTime;
	private final int _activationChance;
	
	private final int _summonTotalLifeTime;
	private final int _summonTimeLostIdle;
	private final int _summonTimeLostActive;
	
	private final int _itemConsumeTime;
	private final int _itemConsumeOT;
	private final int _itemConsumeIdOT;
	private final int _itemConsumeSteps;
	
	public L2SkillSummon(StatsSet set)
	{
		super(set);
		
		_npcId = set.getInteger("npcId", 0); // default for undescribed skills
		_expPenalty = set.getFloat("expPenalty", 0.f);
		_isCubic = set.getBool("isCubic", false);
		
		_activationTime = set.getInteger("activationtime", 8);
		_activationChance = set.getInteger("activationchance", 30);
		
		_summonTotalLifeTime = set.getInteger("summonTotalLifeTime", 1200000); // 20 minutes default
		_summonTimeLostIdle = set.getInteger("summonTimeLostIdle", 0);
		_summonTimeLostActive = set.getInteger("summonTimeLostActive", 0);
		
		_itemConsumeOT = set.getInteger("itemConsumeCountOT", 0);
		_itemConsumeIdOT = set.getInteger("itemConsumeIdOT", 0);
		_itemConsumeTime = set.getInteger("itemConsumeTime", 0);
		_itemConsumeSteps = set.getInteger("itemConsumeSteps", 0);
	}
	
	public boolean checkCondition(Creature activeChar)
	{
		if (activeChar instanceof Player)
		{
			Player player = (Player) activeChar;
			
			if (isCubic())
			{
				// Player is always able to cast mass cubic skill
				if (getTargetType() != SkillTargetType.SELF)
					return true;
				
				if (player.getCubics().size() > player.getSkillLevel(SKILL_CUBIC_MASTERY))
				{
					player.sendPacket(SystemMessageId.CUBIC_SUMMONING_FAILED);
					return false;
				}
			}
			else
			{
				if (player.isInObserverMode())
					return false;
				
				if (player.getSummon() != null)
				{
					player.sendPacket(SystemMessageId.SUMMON_ONLY_ONE);
					return false;
				}
			}
		}
		return super.checkCondition(activeChar, null, false);
	}
	
	@Override
	public void useSkill(Creature caster, WorldObject[] targets)
	{
		if (caster.isAlikeDead() || !(caster instanceof Player))
			return;
		
		Player activeChar = (Player) caster;
		
		if (_npcId == 0)
		{
			activeChar.sendMessage("Summon skill " + getId() + " not described yet");
			return;
		}
		
		if (_isCubic)
		{
			int skillLevel = getLevel();
			if (skillLevel > 100)
				skillLevel = Math.round(((getLevel() - 100) / 7) + 8);
			
			// Mass cubic skill.
			if (targets.length > 1)
			{
				for (WorldObject obj : targets)
				{
					if (!(obj instanceof Player))
						continue;
					
					((Player) obj).addOrRefreshCubic(_npcId, skillLevel, getPower(), _activationTime, _activationChance, _summonTotalLifeTime, (obj != activeChar));
				}
			}
			else
				activeChar.addOrRefreshCubic(_npcId, skillLevel, getPower(), _activationTime, _activationChance, _summonTotalLifeTime, false);
		}
		else
		{
			if (activeChar.getSummon() != null || activeChar.isMounted())
				return;
			
			Servitor summon;
			NpcTemplate summonTemplate = NpcData.getInstance().getTemplate(_npcId);
			if (summonTemplate == null)
			{
				_log.warning("Summon attempt for nonexisting NPC ID: " + _npcId + ", skill ID: " + getId());
				return;
			}
			
			if (summonTemplate.isType("SiegeSummon"))
				summon = new SiegeSummon(IdFactory.getInstance().getNextId(), summonTemplate, activeChar, this);
			else
				summon = new Servitor(IdFactory.getInstance().getNextId(), summonTemplate, activeChar, this);
			
			activeChar.setSummon(summon);
			
			summon.setName(summonTemplate.getName());
			summon.setTitle(activeChar.getName());
			summon.setExpPenalty(_expPenalty);
			summon.setCurrentHp(summon.getMaxHp());
			summon.setCurrentMp(summon.getMaxMp());
			summon.forceRunStance();
			
			final Location spawnLoc = activeChar.getPosition().clone();
			spawnLoc.addStrictOffset(30);
			spawnLoc.set(GeoEngine.getInstance().getValidLocation(activeChar.getPosition(), spawnLoc));
			
			summon.spawnMe(spawnLoc, activeChar.getHeading());
			summon.getAI().setFollowStatus(true);
		}
	}
	
	public final boolean isCubic()
	{
		return _isCubic;
	}
	
	public final int getTotalLifeTime()
	{
		return _summonTotalLifeTime;
	}
	
	public final int getTimeLostIdle()
	{
		return _summonTimeLostIdle;
	}
	
	public final int getTimeLostActive()
	{
		return _summonTimeLostActive;
	}
	
	/**
	 * @return Returns the itemConsume count over time.
	 */
	public final int getItemConsumeOT()
	{
		return _itemConsumeOT;
	}
	
	/**
	 * @return Returns the itemConsumeId over time.
	 */
	public final int getItemConsumeIdOT()
	{
		return _itemConsumeIdOT;
	}
	
	public final int getItemConsumeSteps()
	{
		return _itemConsumeSteps;
	}
	
	/**
	 * @return Returns the itemConsume time in milliseconds.
	 */
	public final int getItemConsumeTime()
	{
		return _itemConsumeTime;
	}
}