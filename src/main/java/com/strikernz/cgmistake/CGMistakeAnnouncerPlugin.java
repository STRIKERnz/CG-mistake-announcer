package com.strikernz.cgmistake;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.util.Objects;
import java.util.Random;

@Slf4j
@PluginDescriptor(
	name = "CG Mistake Announcer",
	description = "Tracks and announces mistakes during Corrupted Gauntlet runs",
	tags = {"gauntlet", "pvm", "bossing", "cg"}
)
public class CGMistakeAnnouncerPlugin extends Plugin
{
	// Corrupted Gauntlet boss NPC ID
	private static final int CORRUPTED_HUNLLEF_NPC_ID = 9036;
	
	// Attack animation IDs for the Corrupted Hunllef
	private static final int HUNLLEF_MELEE_ANIMATION = 8754;
	private static final int HUNLLEF_RANGE_ANIMATION = 8755;
	private static final int HUNLLEF_MAGE_ANIMATION = 8753;
	
	// Overhead text display duration (in game ticks)
	private static final int OVERHEAD_CYCLES = 100;

	@Inject
	private Client client;

	@Inject
	private CGMistakeAnnouncerConfig config;

	private final Random random = new Random();
	
	// Track the last attack style from the boss
	private AttackStyle lastBossAttack = AttackStyle.UNKNOWN;
	
	// Track if we're in the Corrupted Gauntlet
	private boolean inCorruptedGauntlet = false;

	private enum AttackStyle
	{
		MELEE,
		RANGED,
		MAGIC,
		UNKNOWN
	}

	@Override
	protected void startUp() throws Exception
	{
		log.debug("CG Mistake Announcer started!");
		inCorruptedGauntlet = false;
		lastBossAttack = AttackStyle.UNKNOWN;
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("CG Mistake Announcer stopped!");
		inCorruptedGauntlet = false;
		lastBossAttack = AttackStyle.UNKNOWN;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Check if player is in Corrupted Gauntlet area
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			inCorruptedGauntlet = false;
			return;
		}

		// Check for Corrupted Hunllef NPC to determine if we're in the boss room
		boolean bossPresent = false;
		for (NPC npc : client.getNpcs())
		{
			if (npc.getId() == CORRUPTED_HUNLLEF_NPC_ID)
			{
				bossPresent = true;
				break;
			}
		}
		
		inCorruptedGauntlet = bossPresent;
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (!config.enableMistakeTracking() || !inCorruptedGauntlet)
		{
			return;
		}

		if (!(event.getActor() instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) event.getActor();
		
		// Check if it's the Corrupted Hunllef
		if (npc.getId() != CORRUPTED_HUNLLEF_NPC_ID)
		{
			return;
		}

		int animation = npc.getAnimation();
		
		// Detect boss attack style based on animation
		if (animation == HUNLLEF_MELEE_ANIMATION)
		{
			lastBossAttack = AttackStyle.MELEE;
		}
		else if (animation == HUNLLEF_RANGE_ANIMATION)
		{
			lastBossAttack = AttackStyle.RANGED;
		}
		else if (animation == HUNLLEF_MAGE_ANIMATION)
		{
			lastBossAttack = AttackStyle.MAGIC;
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!config.enableMistakeTracking() || !inCorruptedGauntlet)
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null || event.getActor() != localPlayer)
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();
		
		// Only track actual damage (not healing, poison, etc.)
		if (hitsplat.getHitsplatType() != Hitsplat.HitsplatType.DAMAGE)
		{
			return;
		}

		int damage = hitsplat.getAmount();
		if (damage <= 0)
		{
			return;
		}

		boolean mistakeDetected = false;

		// Check if we could have avoided this damage with the correct prayer
		if (config.trackPrayerMisses() && lastBossAttack != AttackStyle.UNKNOWN)
		{
			boolean hasMeleePrayer = client.isPrayerActive(Prayer.PROTECT_FROM_MELEE);
			boolean hasRangePrayer = client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES);
			boolean hasMagePrayer = client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC);

			if (lastBossAttack == AttackStyle.MELEE && !hasMeleePrayer)
			{
				mistakeDetected = true;
			}
			else if (lastBossAttack == AttackStyle.RANGED && !hasRangePrayer)
			{
				mistakeDetected = true;
			}
			else if (lastBossAttack == AttackStyle.MAGIC && !hasMagePrayer)
			{
				mistakeDetected = true;
			}
		}
		else if (config.trackDamage() && lastBossAttack != AttackStyle.UNKNOWN)
		{
			// Track damage only if trackPrayerMisses is disabled
			// This avoids duplicate announcements
			boolean hasMeleePrayer = client.isPrayerActive(Prayer.PROTECT_FROM_MELEE);
			boolean hasRangePrayer = client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES);
			boolean hasMagePrayer = client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC);

			// Only announce if damage was taken with the correct prayer active (unavoidable)
			// or if we can't determine if prayer was correct
			boolean hasCorrectPrayer = false;
			if (lastBossAttack == AttackStyle.MELEE && hasMeleePrayer)
			{
				hasCorrectPrayer = true;
			}
			else if (lastBossAttack == AttackStyle.RANGED && hasRangePrayer)
			{
				hasCorrectPrayer = true;
			}
			else if (lastBossAttack == AttackStyle.MAGIC && hasMagePrayer)
			{
				hasCorrectPrayer = true;
			}

			// Only track as mistake if correct prayer was NOT active
			if (!hasCorrectPrayer)
			{
				mistakeDetected = true;
			}
		}

		if (mistakeDetected)
		{
			announceLocalMistake(localPlayer);
		}
	}

	private void announceLocalMistake(Player player)
	{
		String messagesConfig = config.mistakeMessages();
		if (messagesConfig == null || messagesConfig.trim().isEmpty())
		{
			return;
		}

		// Split by comma and pick one at random
		String[] messages = messagesConfig.split("\\s*,\\s*");
		
		// Filter out any empty strings
		String[] validMessages = new String[messages.length];
		int validCount = 0;
		for (String msg : messages)
		{
			if (msg != null && !msg.trim().isEmpty())
			{
				validMessages[validCount++] = msg.trim();
			}
		}
		
		// If no valid messages, return
		if (validCount == 0)
		{
			return;
		}
		
		String message = validMessages[random.nextInt(validCount)];

		// Display overhead text
		if (config.showOverheadText())
		{
			player.setOverheadText(message);
			player.setOverheadCycle(OVERHEAD_CYCLES);
		}

		// Optional chat message
		if (config.sendToChat())
		{
			client.addChatMessage(
				ChatMessageType.GAMEMESSAGE,
				"",
				message,
				null
			);
		}
	}

	@Provides
	CGMistakeAnnouncerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CGMistakeAnnouncerConfig.class);
	}
}
