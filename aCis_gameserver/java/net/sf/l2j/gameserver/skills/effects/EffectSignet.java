package net.sf.l2j.gameserver.skills.effects;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.gameserver.data.SkillTable;
import net.sf.l2j.gameserver.enums.skills.EffectType;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.instance.EffectPoint;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.skills.AbstractEffect;
import net.sf.l2j.gameserver.skills.L2Skill;
import net.sf.l2j.gameserver.skills.l2skills.L2SkillSignet;

public class EffectSignet extends AbstractEffect
{
	private L2Skill _skill;
	private EffectPoint _actor;
	private boolean _srcInArena;
	
	public EffectSignet(EffectTemplate template, L2Skill skill, Creature effected, Creature effector)
	{
		super(template, skill, effected, effector);
	}
	
	@Override
	public EffectType getEffectType()
	{
		return EffectType.SIGNET_EFFECT;
	}
	
	@Override
	public boolean onStart()
	{
		_skill = SkillTable.getInstance().getInfo(((L2SkillSignet) getSkill()).effectId, getSkill().getLevel());
		_actor = (EffectPoint) getEffected();
		_srcInArena = getEffector().isInArena();
		return true;
	}
	
	@Override
	public boolean onActionTime()
	{
		if (_skill == null)
			return true;
		
		final int mpConsume = _skill.getMpConsume();
		if (mpConsume > getEffector().getStatus().getMp())
		{
			getEffector().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SKILL_REMOVED_DUE_LACK_MP));
			return false;
		}
		
		getEffector().getStatus().reduceMp(mpConsume);
		
		List<Creature> targets = new ArrayList<>();
		for (Creature cha : _actor.getKnownTypeInRadius(Creature.class, getSkill().getSkillRadius()))
		{
			// isSignetOffensiveSkill only really checks for Day of Doom, the other signets ahve different Effects
			if (_skill.isSignetOffensiveSkill() && !L2Skill.checkForAreaOffensiveSkills(getEffector(), cha, _skill, true, _srcInArena))
				continue;
			
			// there doesn't seem to be a visible effect with MagicSkillLaunched packet...
			_actor.broadcastPacket(new MagicSkillUse(_actor, cha, _skill.getId(), _skill.getLevel(), 0, 0));
			
			targets.add(cha);
		}
		
		if (!targets.isEmpty())
			getEffector().getCast().callSkill(_skill, targets.toArray(new Creature[targets.size()]));
		
		return true;
	}
	
	@Override
	public void onExit()
	{
		if (_actor != null)
			_actor.deleteMe();
	}
}