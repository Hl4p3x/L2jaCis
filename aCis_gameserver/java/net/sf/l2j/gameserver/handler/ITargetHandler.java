package net.sf.l2j.gameserver.handler;

import net.sf.l2j.gameserver.enums.skills.SkillTargetType;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.holder.SkillUseHolder;
import net.sf.l2j.gameserver.skills.L2Skill;

public interface ITargetHandler
{
	static final Creature[] EMPTY_TARGET_ARRAY = new Creature[0];
	
	/**
	 * The worker method called by a {@link Creature} when using a {@link L2Skill}.
	 * @param skillUseHolder The {@link SkillUseHolder} containing the skill use information (skill, target, isCtrlPressed, isShiftPressed).
	 * @return The array of valid {@link WorldObject} targets, based on the {@link Creature} caster, {@link Creature} target and {@link L2Skill} set as parameters.
	 */
	public Creature[] getTargetList(SkillUseHolder skillUseHolder);
	
	/**
	 * @return The associated {@link SkillTargetType}.
	 */
	public SkillTargetType getTargetType();
	
	/**
	 * @param target
	 * @param caster
	 * @param skill
	 * @param isCtrlPressed
	 * @return The real {@link Creature} target.
	 */
	public Creature getFinalTarget(Creature target, Creature caster, L2Skill skill, boolean isCtrlPressed);
}