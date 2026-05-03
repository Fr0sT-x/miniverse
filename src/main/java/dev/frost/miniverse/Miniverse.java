package dev.frost.miniverse;

import dev.frost.miniverse.command.ManhuntCommands;
import dev.frost.miniverse.command.SpeedrunCommands;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.session.SessionCommands;
import dev.frost.miniverse.session.SessionRoutingEvents;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntGameEvents;
import dev.frost.miniverse.minigame.impl.speedrun.SpeedrunGameEvents;
import dev.frost.miniverse.network.SessionNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Miniverse implements ModInitializer {
	public static final String MOD_ID = "miniverse";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			{
				ManhuntCommands.register(dispatcher);
				SpeedrunCommands.register(dispatcher);
				SessionCommands.register(dispatcher);
			}
		);
		ManhuntGameEvents.register();
		SpeedrunGameEvents.register();
		SessionRoutingEvents.register();

		// Register shared session GUI payloads and server-side receivers.
		NetworkConstants.registerPayloadTypes();
		SessionNetwork.register();

		LOGGER.info("Miniverse initialized. Manhunt, Speedrun, and session commands registered.");
	}
}