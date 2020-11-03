package net.sf.l2j.gameserver.skills.effects;

import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.enums.skills.EffectType;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.EffectPoint;
import net.sf.l2j.gameserver.skills.AbstractEffect;
import net.sf.l2j.gameserver.skills.L2Skill;

public class EffectSignetNoise extends AbstractEffect
{
	private EffectPoint _actor;
	private boolean _isCtrlPressed;
	
	public EffectSignetNoise(EffectTemplate template, L2Skill skill, Creature effected, Creature effector)
	{
		super(template, skill, effected, effector);
	}
	
	@Override
	public EffectType getEffectType()
	{
		return EffectType.SIGNET_GROUND;
	}
	
	@Override
	public boolean onStart()
	{
		_actor = (EffectPoint) getEffected();
		_isCtrlPressed = ((Player) getEffector()).getAI().getCurrentIntention().isCtrlPressed();
		return true;
	}
	
	@Override
	public boolean onActionTime()
	{
		if (getCount() == getTemplate().getCounter() - 1)
			return true; // do nothing first time
			
		Player caster = (Player) getEffector();
		for (Playable cha : _actor.getKnownTypeInRadius(Playable.class, getSkill().getSkillRadius()))
		{
			if (caster == cha.getActingPlayer())
				continue;
			
			if (cha.isDead())
				continue;
			
			if (cha.isInsideZone(ZoneId.PEACE))
				continue;
			
			if (caster.canCastOffensiveSkillOnPlayable(cha, _skill, _isCtrlPressed))
			{
				for (AbstractEffect effect : cha.getAllEffects())
				{
					if (effect.getSkill().isDance())
						effect.exit();
				}
			}
		}
		return true;
	}
	
	@Override
	public void onExit()
	{
		if (_actor != null)
			_actor.deleteMe();
	}
}