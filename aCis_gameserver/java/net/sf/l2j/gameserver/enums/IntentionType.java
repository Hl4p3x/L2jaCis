package net.sf.l2j.gameserver.enums;

/**
 * Enumeration of generic intentions of an actor.
 */
public enum IntentionType
{
	/** Stop all actions and do nothing. In case of Npc, disconnect AI if no players around. */
	IDLE,
	/** Alerted state without goal : scan attackable targets, random walk, etc. */
	ACTIVE,
	/** Rest (sit until attacked). */
	SIT,
	/** Stand Up. */
	STAND,
	/** Move to target if too far, then attack it - may be ignored (another target, invalid zoning, etc). */
	ATTACK,
	/** Move to target if too far, then cast a spell. */
	CAST,
	/** Move to another location. */
	MOVE_TO,
	/** Check target's movement and follow it. */
	FOLLOW,
	/** Move to target if too far, then pick up the item. */
	PICK_UP,
	/** Move to target if too far, then interact. */
	INTERACT,
	/** Use an Item. */
	USE_ITEM,
	/** Fake death. */
	FAKE_DEATH;
}