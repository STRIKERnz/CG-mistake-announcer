package com.strikernz.cgmistake;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("cgmistake")
public interface CGMistakeAnnouncerConfig extends Config
{
	@ConfigItem(
		keyName = "enableMistakeTracking",
		name = "Enable Mistake Tracking",
		description = "Track and announce mistakes during CG runs"
	)
	default boolean enableMistakeTracking()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOverheadText",
		name = "Show Overhead Text",
		description = "Display mistake message as overhead text on player"
	)
	default boolean showOverheadText()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sendToChat",
		name = "Send to Chat",
		description = "Also send the mistake message to the chat box"
	)
	default boolean sendToChat()
	{
		return false;
	}

	// Prayer mistake tracking
	@ConfigItem(
		keyName = "trackMeleePrayerMisses",
		name = "Track Melee Prayer Misses",
		description = "Announce when you take damage from melee without Protect from Melee active"
	)
	default boolean trackMeleePrayerMisses()
	{
		return true;
	}

	@ConfigItem(
		keyName = "meleePrayerMessages",
		name = "Melee Prayer Messages",
		description = "Comma-separated messages for melee prayer misses"
	)
	default String meleePrayerMessages()
	{
		return "Get slammed!, Miss clicked!, Heavy!, Dunked!, Oh Boy!";
	}

	@ConfigItem(
		keyName = "trackRangePrayerMisses",
		name = "Track Range Prayer Misses",
		description = "Announce when you take damage from range without Protect from Missiles active"
	)
	default boolean trackRangePrayerMisses()
	{
		return true;
	}

	@ConfigItem(
		keyName = "rangePrayerMessages",
		name = "Range Prayer Messages",
		description = "Comma-separated messages for range prayer misses"
	)
	default String rangePrayerMessages()
	{
		return "Arrows hurt!, Got sniped!, Pew pew!, That one had my name on it!";
	}

	@ConfigItem(
		keyName = "trackMagePrayerMisses",
		name = "Track Mage Prayer Misses",
		description = "Announce when you take damage from magic without Protect from Magic active"
	)
	default boolean trackMagePrayerMisses()
	{
		return true;
	}

	@ConfigItem(
		keyName = "magePrayerMessages",
		name = "Mage Prayer Messages",
		description = "Comma-separated messages for mage prayer misses"
	)
	default String magePrayerMessages()
	{
		return "Zap!, Lightning BOLT!, I got blasted!, That spell stung!";
	}

	// Tornado tracking
	@ConfigItem(
		keyName = "trackTornadoes",
		name = "Track Tornado Hits",
		description = "Announce when you stand on a tornado"
	)
	default boolean trackTornadoes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tornadoMessages",
		name = "Tornado Hit Messages",
		description = "Comma-separated messages for tornado hits"
	)
	default String tornadoMessages()
	{
		return "Why am I in tornado?, Tornado!, Move out of tornado!";
	}

	// Floor damage tracking
	@ConfigItem(
		keyName = "trackFloorDamage",
		name = "Track Floor Damage",
		description = "Announce when you stand on burning floor tiles"
	)
	default boolean trackFloorDamage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "floorDamageMessages",
		name = "Floor Damage Messages",
		description = "Comma-separated messages for floor damage"
	)
	default String floorDamageMessages()
	{
		return "The floor is lava!, Fire! Fire! FIRE!!!, Floor damage!";
	}
}
