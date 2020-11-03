package net.sf.l2j.gameserver.model.holder;

import net.sf.l2j.gameserver.handler.ITargetHandler;
import net.sf.l2j.gameserver.handler.TargetHandler;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.skills.L2Skill;

/**
 * Skill casting information (used as a structure for any skill cast of Creature)
 **/
public class SkillUseHolder
{
	private final L2Skill _skill;
	private final Creature _target;
	private final Creature _caster;
	private final Creature _finalTarget;
	private final boolean _isCtrlPressed;
	private final boolean _isShiftPressed;
	
	public SkillUseHolder(Creature caster, Creature target, L2Skill skill, boolean isCtrlPressed, boolean isShiftPressed)
	{
		_skill = skill;
		_isCtrlPressed = isCtrlPressed;
		_isShiftPressed = isShiftPressed;
		_target = target;
		_caster = caster;
		_finalTarget = getCastTargetFor();
	}
	
	private final Creature getCastTargetFor()
	{
		final ITargetHandler handler = TargetHandler.getInstance().getHandler(_skill.getTargetType());
		if (handler != null)
			return handler.getFinalTarget(_target, _caster, _skill, _isCtrlPressed);
		
		_caster.sendMessage(_skill.getTargetType() + " skill target type isn't currently handled.");
		return null;
	}
	
	public L2Skill getSkill()
	{
		return _skill;
	}
	
	public Creature getFinalTarget()
	{
		return _finalTarget;
	}
	
	public Creature getCaster()
	{
		return _caster;
	}
	
	public boolean isCtrlPressed()
	{
		return _isCtrlPressed;
	}
	
	public boolean isShiftPressed()
	{
		return _isShiftPressed;
	}
	
	/**
	 * @return a Creature[] consisting of all targets, depending on the skill type.
	 */
	public final Creature[] getTargetList()
	{
		final ITargetHandler handler = TargetHandler.getInstance().getHandler(_skill.getTargetType());
		if (handler != null)
			return handler.getTargetList(this);
		
		_caster.sendMessage(_skill.getTargetType() + " skill target type isn't currently handled.");
		return ITargetHandler.EMPTY_TARGET_ARRAY;
	}
	
	@Override
	public String toString()
	{
		return "SkillUseHolder [skill=" + ((_skill == null) ? "none" : _skill.getName()) + " target=" + ((_target == null) ? "none" : _target.getName()) + " ctrl=" + _isCtrlPressed + " shift=" + _isShiftPressed + "]";
	}
}