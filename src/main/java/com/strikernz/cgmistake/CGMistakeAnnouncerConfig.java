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
		keyName = "mistakeMessages",
		name = "Mistake Messages",
		description = "Comma-separated list of messages shown when a mistake is detected. Do not use commas within individual messages."
	)
	default String mistakeMessages()
	{
		return "Oops!, My bad!, I need to focus!, That was not optimal!";
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

	@ConfigItem(
		keyName = "trackPrayerMisses",
		name = "Track Prayer Misses",
		description = "Announce when you take damage without the correct prayer active"
	)
	default boolean trackPrayerMisses()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackDamage",
		name = "Track Avoidable Damage",
		description = "Announce when you take damage that could have been avoided"
	)
	default boolean trackDamage()
	{
		return true;
	}
}
