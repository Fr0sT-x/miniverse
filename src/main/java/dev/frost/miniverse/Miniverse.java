package dev.frost.miniverse;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.map.MapEditorCommands;
import dev.frost.miniverse.map.MapEditorEvents;
import dev.frost.miniverse.map.editor.MapEditorNetwork;
import dev.frost.miniverse.map.editor.MapEditorPlacementController;
import dev.frost.miniverse.minigame.MiniverseGames;
import dev.frost.miniverse.minigame.core.event.MinigameEventRouter;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleCommands;
import dev.frost.miniverse.minigame.core.MinigameRegistry;
import dev.frost.miniverse.session.SessionCommands;
import dev.frost.miniverse.session.SessionRecoveryService;
import dev.frost.miniverse.session.SessionRoutingEvents;
import dev.frost.miniverse.network.SessionNetwork;
import dev.frost.miniverse.network.TransitionTransferCoordinator;
import dev.frost.miniverse.network.ClientConnectionHosts;
import dev.frost.miniverse.session.SessionRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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

		dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjectiveRegistry.register();
		MiniverseGames.registerAll();
		SessionRegistry.cleanupSessionsOnStartup();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			{
				MinigameRegistry.registerCommands(dispatcher);
				SessionCommands.register(dispatcher);
				MatchLifecycleCommands.register(dispatcher);
				MapEditorCommands.register(dispatcher);
			}
		);
		MinigameEventRouter.register();
		MinigameRegistry.registerEvents();
		SessionRoutingEvents.register();
		MapEditorEvents.register();
		MapEditorPlacementController.register();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SessionRecoveryService.recoverUnfinishedSessions(server);
			dev.frost.miniverse.minigame.core.kit.KitRegistry.loadCustomKits(server);
		});

		// Register shared session GUI payloads and server-side receivers.
		NetworkConstants.registerPayloadTypes();
		ClientConnectionHosts.register();
		SessionNetwork.register();
		MapEditorNetwork.register();
		TransitionTransferCoordinator.register();

		LOGGER.info("Miniverse initialized. {} minigame(s) registered.", MinigameRegistry.getDefinitions().size());
	}
}
