package com.strikernz.cgmistake;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
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
	// Corrupted Gauntlet boss NPC IDs (different IDs for different protection prayers)
	// Crystalline variants: base, range, mage, melee
	// Corrupted variants: base, range, mage, melee
	private static final List<Integer> HUNLLEF_IDS = List.of(
		9021, // Crystalline Hunllef (base - melee protect)
		9022, // Crystalline Hunllef (range variant - protect from missiles)
		9023, // Crystalline Hunllef (mage variant - protect from magic)
		9024, // Crystalline Hunllef (melee variant)
		9035, // Corrupted Hunllef (base - melee protect)
		9036, // Corrupted Hunllef (range variant - protect from missiles)
		9037, // Corrupted Hunllef (mage variant - protect from magic)
		9038  // Corrupted Hunllef (melee variant)
	);

	// Tornado NPC IDs
	private static final List<Integer> TORNADO_IDS = List.of(9025, 9039);

	// Varbits (match GauntletPerformanceTracker approach)
	private static final int GAUNTLET_BOSS_START_VARBIT = 9177;
	private static final int GAUNTLET_MAZE_VARBIT = 9178;

	// Prayer varbit IDs (use these instead of deprecated Prayer API)
	private static final int VARBIT_PROTECT_FROM_MAGIC = 5490;
	private static final int VARBIT_PROTECT_FROM_MISSILES = 5489;
	private static final int VARBIT_PROTECT_FROM_MELEE = 5491;

	// Attack animation IDs for the Corrupted Hunllef
	private static final int HUNLLEF_ATTACK_ANIMATION = 8419;
	private static final int HUNLLEF_MAGE_SWITCH_ANIMATION = 8754;
	private static final int HUNLLEF_RANGE_SWITCH_ANIMATION = 8755;

	// Ground object IDs
	private static final int DAMAGE_TILE_ID = 36048;

	// Overhead text display duration (in game ticks)
	private static final int OVERHEAD_CYCLES = 100;

	@Inject
	private Client client;

	@Inject
	private CGMistakeAnnouncerConfig config;

	private final Random random = new Random();
	
	// Track the boss and tornadoes
	private NPC hunllef;
	private final List<NPC> tornadoes = new ArrayList<>();

	// Track if boss is currently using mage (true) or range (false)
	private boolean isHunllefMaging = false;

	// Track if we're in the Corrupted Gauntlet (use varbit or presence of hunllef)
	private boolean inCorruptedGauntlet = false;

	// Track recent boss attack pending info
	// pendingAttackType: 0 = none, 1 = mage, 2 = range, 3 = melee
	private int pendingAttackType = 0;
	private boolean pendingAttackHadCorrectPrayer = false;
	private int lastBossAttackTick = 0;
	private static final int ATTACK_DAMAGE_WINDOW_TICKS = 3; // Time window to correlate attack with damage

	// Debounce for tornado/floor damage to prevent spam
	private int lastTornadoAnnounceTick = 0;
	private int lastFloorAnnounceTick = 0;
	private static final int ANNOUNCE_COOLDOWN_TICKS = 3;

	private enum MistakeType
	{
		MELEE_PRAYER,
		RANGE_PRAYER,
		MAGE_PRAYER,
		TORNADO,
		FLOOR_DAMAGE
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("=== CG Mistake Announcer started! ===");
		log.info("Config - enableMistakeTracking: {}", config.enableMistakeTracking());
		log.info("Config - showOverheadText: {}", config.showOverheadText());
		log.info("Config - sendToChat: {}", config.sendToChat());
		resetTracking();
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("=== CG Mistake Announcer stopped! ===");
		resetTracking();
	}

	private void resetTracking()
	{
		inCorruptedGauntlet = false;
		hunllef = null;
		tornadoes.clear();
		isHunllefMaging = false;
		pendingAttackType = 0;
		pendingAttackHadCorrectPrayer = false;
		lastBossAttackTick = 0;
		lastTornadoAnnounceTick = 0;
		lastFloorAnnounceTick = 0;
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		// Ignore varbit changes - we track encounter state via hunllef NPC presence only
		// The varbit is unreliable and toggles frequently, causing false encounter end events
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Check player and bail if not in gauntlet according to varbit or hunllef presence
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null || !config.enableMistakeTracking())
		{
			return;
		}

		// Treat either the varbit or having the hunllef NPC as being in the encounter
		boolean isInEncounter = inCorruptedGauntlet || hunllef != null;
		if (!isInEncounter)
		{
			return;
		}

		int currentTick = client.getTickCount();

		// Clear old pending attack if damage window expired
		if (pendingAttackType != 0 && currentTick - lastBossAttackTick > ATTACK_DAMAGE_WINDOW_TICKS)
		{
			pendingAttackType = 0;
			pendingAttackHadCorrectPrayer = false;
		}

		WorldPoint playerLocation = localPlayer.getWorldLocation();

		// Check for tornado hits
		if (config.trackTornadoes() && currentTick - lastTornadoAnnounceTick >= ANNOUNCE_COOLDOWN_TICKS)
		{
			for (NPC tornado : tornadoes)
			{
				if (tornado != null && playerLocation.equals(tornado.getWorldLocation()))
				{
					announceMistake(MistakeType.TORNADO);
					lastTornadoAnnounceTick = currentTick;
					break;
				}
			}
		}

		// Check for floor damage - follow powerrus117 pattern (uses world view scene)
		if (config.trackFloorDamage() && currentTick - lastFloorAnnounceTick >= ANNOUNCE_COOLDOWN_TICKS)
		{
			try
			{
				var scene = client.getWorldView(localPlayer.getLocalLocation().getWorldView()).getScene();
				var tiles = scene.getTiles();
				int tileX = playerLocation.getX() - scene.getBaseX();
				int tileY = playerLocation.getY() - scene.getBaseY();

				if (tileX >= 0 && tileY >= 0 && tileX < tiles[playerLocation.getPlane()].length
					&& tileY < tiles[playerLocation.getPlane()][tileX].length)
				{
					var currentTile = tiles[playerLocation.getPlane()][tileX][tileY];
					if (currentTile != null && currentTile.getGroundObject() != null
						&& currentTile.getGroundObject().getId() == DAMAGE_TILE_ID)
					{
						announceMistake(MistakeType.FLOOR_DAMAGE);
						lastFloorAnnounceTick = currentTick;
					}
				}
			}
			catch (Exception e)
			{
				log.debug("Error checking floor damage: {}", e.getMessage());
			}
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (!config.enableMistakeTracking())
		{
			return;
		}

		if (!(event.getActor() instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) event.getActor();
		
		// Check if it's Hunllef
		if (!HUNLLEF_IDS.contains(npc.getId()))
		{
			return;
		}

		int animation = npc.getAnimation();
		
		// Track boss style switches
		if (animation == HUNLLEF_MAGE_SWITCH_ANIMATION)
		{
			isHunllefMaging = true;
			log.info("Hunllef switched to mage");
		}
		else if (animation == HUNLLEF_RANGE_SWITCH_ANIMATION)
		{
			isHunllefMaging = false;
			log.info("Hunllef switched to range");
		}
		// Check for boss attack animation
		else if (animation == HUNLLEF_ATTACK_ANIMATION)
		{
			// Record the attack type and whether the player had the correct prayer at the time of the attack
			lastBossAttackTick = client.getTickCount();
			int attackType = getAttackTypeFromNpc(npc);
			if (attackType == 0)
			{
				// Fall back to the tracking flag if we couldn't determine it from the npc id
				attackType = isHunllefMaging ? 1 : 2;
			}
			pendingAttackType = attackType;
			pendingAttackHadCorrectPrayer = isCorrectPrayerActiveForAttackType(attackType);
			log.info("Hunllef ATTACK: type={} (1=mage,2=range,3=melee) hadCorrectPrayer={}", attackType, pendingAttackHadCorrectPrayer);
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		final NPC npc = event.getNpc();

		if (TORNADO_IDS.contains(npc.getId()))
		{
			tornadoes.add(npc);
		}
		else if (HUNLLEF_IDS.contains(npc.getId()))
		{
			hunllef = npc;
			// Presence of the hunllef indicates the encounter is active even if varbit didn't flip
			inCorruptedGauntlet = true;
			// Reset transient state only - do NOT call resetTracking() which would clear hunllef!
			pendingAttackType = 0;
			pendingAttackHadCorrectPrayer = false;
			lastBossAttackTick = client.getTickCount();
			log.info("=== HUNLLEF SPAWNED (id={}) - encounter now ACTIVE ===", npc.getId());
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		final NPC npc = event.getNpc();

		if (TORNADO_IDS.contains(npc.getId()))
		{
			tornadoes.removeIf(t -> t == npc);
		}
		else if (HUNLLEF_IDS.contains(npc.getId()))
		{
			hunllef = null;
			resetTracking();
			log.info("=== HUNLLEF DESPAWNED - encounter ENDED ===");
		}
	}

	@Subscribe(priority = -100) // High priority to receive event before other plugins
	public void onHitsplatApplied(HitsplatApplied event)
	{
		log.info("onHitsplatApplied called - actor: {}", event.getActor() != null ? event.getActor().getName() : "null");

		if (!config.enableMistakeTracking())
		{
			log.info("Exiting - mistake tracking disabled");
			return;
		}

		// Treat either the varbit or having the hunllef NPC as being in the encounter
		boolean isInEncounter = inCorruptedGauntlet || hunllef != null;
		if (!isInEncounter)
		{
			log.info("Exiting - not in encounter (inCorruptedGauntlet={}, hunllef={})", inCorruptedGauntlet, hunllef != null);
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null || event.getActor() != localPlayer)
		{
			log.info("Exiting - not local player (localPlayer={}, eventActor={})",
				localPlayer != null ? localPlayer.getName() : "null",
				event.getActor() != null ? event.getActor().getName() : "null");
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();

		log.info("HITSPLAT: amount={} actor={} pendingType={} tick={}", hitsplat.getAmount(), event.getActor().getName(), pendingAttackType, client.getTickCount());

		// Only track actual damage (type 0 = regular damage, not healing, poison, etc.)
		// We also want to exclude 0 damage splats
		int damage = hitsplat.getAmount();
		if (damage <= 0)
		{
			return;
		}

		int currentTick = client.getTickCount();
		// If the hitsplat isn't within the recent boss attack window, ignore
		if (currentTick - lastBossAttackTick > ATTACK_DAMAGE_WINDOW_TICKS)
		{
			log.info("Hitsplat outside attack window (ticks since attack: {})", currentTick - lastBossAttackTick);
			// clear stale pending attack
			pendingAttackType = 0;
			pendingAttackHadCorrectPrayer = false;
			return;
		}

		// Check if this damage corresponds to the pending attack and whether prayer at attack time was correct
		if (pendingAttackType == 1 && config.trackMagePrayerMisses())
		{
			log.info("MAGE hitsplat: damage={} hadCorrectPrayerAtAttack={}", damage, pendingAttackHadCorrectPrayer);
			// Only announce if prayer was NOT active at the time of the attack
			if (!pendingAttackHadCorrectPrayer)
			{
				log.info(">>> ANNOUNCING MAGE MISTAKE <<<");
				announceMistake(MistakeType.MAGE_PRAYER);
			}

			// Clear pending attack after processing
			pendingAttackType = 0;
			pendingAttackHadCorrectPrayer = false;
		}
		else if (pendingAttackType == 2 && config.trackRangePrayerMisses())
		{
			log.info("RANGE hitsplat: damage={} hadCorrectPrayerAtAttack={}", damage, pendingAttackHadCorrectPrayer);
			// Only announce if prayer was NOT active at the time of the attack
			if (!pendingAttackHadCorrectPrayer)
			{
				log.info(">>> ANNOUNCING RANGE MISTAKE <<<");
				announceMistake(MistakeType.RANGE_PRAYER);
			}

			// Clear pending attack after processing
			pendingAttackType = 0;
			pendingAttackHadCorrectPrayer = false;
		}
		else if (pendingAttackType == 3 && config.trackMeleePrayerMisses())
		{
			log.info("MELEE hitsplat: damage={} hadCorrectPrayerAtAttack={}", damage, pendingAttackHadCorrectPrayer);
			// Only announce if prayer was NOT active at the time of the attack
			if (!pendingAttackHadCorrectPrayer)
			{
				log.info(">>> ANNOUNCING MELEE MISTAKE <<<");
				announceMistake(MistakeType.MELEE_PRAYER);
			}

			pendingAttackType = 0;
			pendingAttackHadCorrectPrayer = false;
		}
	}

	private boolean hasCorrectDefensivePrayerActive()
	{
		// Use varbit checks instead of deprecated prayer API
		if (isHunllefMaging)
		{
			return client.getVarbitValue(VARBIT_PROTECT_FROM_MAGIC) == 1;
		}
		else
		{
			return client.getVarbitValue(VARBIT_PROTECT_FROM_MISSILES) == 1;
		}
	}

	/**
	 * Derive the attack type from the Hunllef NPC id where possible.
	 * Mapping:
	 * 9023 / 9037 -> mage attack
	 * 9022 / 9036 -> range attack
	 * 9024 / 9038 -> melee attack
	 * Returns: 0 = unknown, 1 = mage, 2 = range, 3 = melee
	 */
	private int getAttackTypeFromNpc(NPC npc)
	{
		int id = npc.getId();
		// Mage variants
		if (id == 9023 || id == 9037)
		{
			return 1;
		}
		// Range variants
		if (id == 9022 || id == 9036)
		{
			return 2;
		}
		// Melee variants
		if (id == 9024 || id == 9038)
		{
			return 3;
		}
		return 0;
	}

	private boolean isCorrectPrayerActiveForAttackType(int attackType)
	{
		switch (attackType)
		{
			case 1:
				return client.getVarbitValue(VARBIT_PROTECT_FROM_MAGIC) == 1;
			case 2:
				return client.getVarbitValue(VARBIT_PROTECT_FROM_MISSILES) == 1;
			case 3:
				return client.getVarbitValue(VARBIT_PROTECT_FROM_MELEE) == 1;
			default:
				return false;
		}
	}

	private void announceMistake(MistakeType mistakeType)
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		// Get the appropriate message config based on mistake type
		String messagesConfig = null;
		switch (mistakeType)
		{
			case MELEE_PRAYER:
				messagesConfig = config.meleePrayerMessages();
				break;
			case RANGE_PRAYER:
				messagesConfig = config.rangePrayerMessages();
				break;
			case MAGE_PRAYER:
				messagesConfig = config.magePrayerMessages();
				break;
			case TORNADO:
				messagesConfig = config.tornadoMessages();
				break;
			case FLOOR_DAMAGE:
				messagesConfig = config.floorDamageMessages();
				break;
		}

		if (messagesConfig == null || messagesConfig.trim().isEmpty())
		{
			return;
		}

		// Split by comma and filter out empty strings
		String[] messages = messagesConfig.split("\\s*,\\s*");
		List<String> validMessages = new ArrayList<>();

		for (String msg : messages)
		{
			if (msg != null && !msg.trim().isEmpty())
			{
				validMessages.add(msg.trim());
			}
		}
		
		// If no valid messages, return
		if (validMessages.isEmpty())
		{
			return;
		}
		
		String message = validMessages.get(random.nextInt(validMessages.size()));

		log.info("=== ANNOUNCING MISTAKE ===");
		log.info("Type: {}", mistakeType);
		log.info("Message: {}", message);
		log.info("showOverheadText: {}", config.showOverheadText());
		log.info("sendToChat: {}", config.sendToChat());

		// Display overhead text
		if (config.showOverheadText())
		{
			localPlayer.setOverheadText(message);
			localPlayer.setOverheadCycle(OVERHEAD_CYCLES);
			log.info("Set overhead text");
		}

		// Optional chat message
		if (config.sendToChat())
		{
			client.addChatMessage(
				ChatMessageType.PUBLICCHAT,
				Objects.requireNonNull(localPlayer.getName()),
				message,
				null
			);
			log.info("Sent chat message");
		}

		log.info("=========================");
	}

	@Provides
	CGMistakeAnnouncerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CGMistakeAnnouncerConfig.class);
	}
}
