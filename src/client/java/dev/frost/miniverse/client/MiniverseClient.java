package dev.frost.miniverse.client;

import dev.frost.miniverse.client.freeze.ClientFreezeHandler;
import dev.frost.miniverse.client.freeze.ClientFreezeState;
import dev.frost.miniverse.client.gui.SessionLaunchStatus;
import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.minigame.layout.InventoryLayoutClient;
import dev.frost.miniverse.client.protection.ProtectionOverlayClient;
import dev.frost.miniverse.client.transition.TransitionOverlay;
import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorContext;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorState;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorScreen;
import dev.frost.miniverse.client.gui.selector.providers.BlockRegistryProvider;
import net.minecraft.block.Block;
import java.util.Set;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class MiniverseClient implements ClientModInitializer {
	public static KeyBinding OPEN_GUI_KEY;
	public static KeyBinding TOGGLE_EDITOR_KEY;
	public static KeyBinding RELOAD_WEAPON_KEY;
	private static boolean pendingSessionOpen;
	private static int pendingScreenshotTicks = 0;
	private static java.io.File pendingScreenshotDir = null;
	private static String pendingScreenshotName = null;
	private static net.minecraft.client.gui.screen.Screen suspendedScreen = null;
	
	public static boolean isScreenshotPending() {
		return pendingScreenshotTicks > 0;
	}

	@Override
	public void onInitializeClient() {
		dev.frost.miniverse.client.gui.ui.UiPreferences.load();
		NetworkConstants.registerPayloadTypes();
		NightVisionToggle.register();
		ClientFreezeHandler.register();
		TransitionOverlay.register();
		ProtectionOverlayClient.register();
		SessionLaunchStatus.register();
		InventoryLayoutClient.register();
		dev.frost.miniverse.client.gui.map.MapEditorOverlayClient.register();
		dev.frost.miniverse.client.gui.map.MapEditorRenderIntegration.register();
		dev.frost.miniverse.client.gui.map.DuelsEditorClient.register();

		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
			if (player == null) {
				return net.minecraft.util.TypedActionResult.pass(net.minecraft.item.ItemStack.EMPTY);
			}
			net.minecraft.item.ItemStack stack = player.getStackInHand(hand);

			// Map Editor air right-click forwarder
			if (dev.frost.miniverse.client.gui.map.MapEditorState.INSTANCE.editorActive && hand == net.minecraft.util.Hand.MAIN_HAND) {
				if (stack.isOf(net.minecraft.item.Items.LIME_DYE)
					|| stack.isOf(net.minecraft.item.Items.RED_DYE)
					|| stack.isOf(net.minecraft.item.Items.GREEN_DYE)
					|| stack.isOf(net.minecraft.item.Items.BARRIER)
					|| stack.isOf(net.minecraft.item.Items.BLAZE_ROD)
					|| stack.isOf(net.minecraft.item.Items.SHEARS)) {
					return net.minecraft.util.TypedActionResult.success(stack);
				}
			}

			// Zombies-only right-click prevention
			boolean isZombiesActive = TransitionOverlay.isZombiesActive()
				|| (dev.frost.miniverse.client.gui.SessionSnapshotData.sessions() != null && dev.frost.miniverse.client.gui.SessionSnapshotData.sessions().stream()
					.anyMatch(s -> "zombies".equalsIgnoreCase(s.game()) && !"STOPPED".equalsIgnoreCase(s.state())));
			if (isZombiesActive) {
				int slot = player.getInventory().selectedSlot;
				if (dev.frost.miniverse.minigame.impl.zombies.item.ZombiesHotbarManager.isPerkSlot(slot)
					|| dev.frost.miniverse.minigame.core.item.ProtectedItemTags.hasType(stack, dev.frost.miniverse.minigame.core.item.ProtectedItemTypes.ZOMBIES_PERK)
					|| dev.frost.miniverse.minigame.core.item.ProtectedItemTags.hasType(stack, dev.frost.miniverse.minigame.core.item.ProtectedItemTypes.ZOMBIES_PLACEHOLDER)) {
					return net.minecraft.util.TypedActionResult.fail(stack);
				}
			}
			return net.minecraft.util.TypedActionResult.pass(stack);
		});

		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (player == null) {
				return net.minecraft.util.ActionResult.PASS;
			}
			net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
			boolean isZombiesActive = TransitionOverlay.isZombiesActive()
				|| (dev.frost.miniverse.client.gui.SessionSnapshotData.sessions() != null && dev.frost.miniverse.client.gui.SessionSnapshotData.sessions().stream()
					.anyMatch(s -> "zombies".equalsIgnoreCase(s.game()) && !"STOPPED".equalsIgnoreCase(s.state())));
			if (isZombiesActive) {
				if (dev.frost.miniverse.minigame.core.item.ProtectedItemTags.hasType(stack, dev.frost.miniverse.minigame.core.item.ProtectedItemTypes.ZOMBIES_PERK)
					|| dev.frost.miniverse.minigame.core.item.ProtectedItemTags.hasType(stack, dev.frost.miniverse.minigame.core.item.ProtectedItemTypes.ZOMBIES_PLACEHOLDER)) {
					return net.minecraft.util.ActionResult.FAIL;
				}
			}
			return net.minecraft.util.ActionResult.PASS;
		});

		OPEN_GUI_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.miniverse.open_gui",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_RIGHT_SHIFT,
			"category." + NetworkConstants.MOD_ID + ".miniverse"
		));

		TOGGLE_EDITOR_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.miniverse.toggle_editor",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_F5,
			"category." + NetworkConstants.MOD_ID + ".miniverse"
		));

		RELOAD_WEAPON_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.miniverse.reload_weapon",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_R,
			"category." + NetworkConstants.MOD_ID + ".miniverse"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_GUI_KEY.wasPressed()) {
				openGui();
			}
			while (TOGGLE_EDITOR_KEY.wasPressed()) {
				if (client.currentScreen instanceof dev.frost.miniverse.client.gui.map.MapEditorWorkspaceScreen) {
					client.setScreen(null);
				} else {
					client.setScreen(new dev.frost.miniverse.client.gui.map.MapEditorWorkspaceScreen());
				}
			}
			while (RELOAD_WEAPON_KEY.wasPressed()) {
				if (client.player != null && client.getNetworkHandler() != null) {
					net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new NetworkConstants.ZombiesReloadPayload());
				}
			}
			if (pendingScreenshotTicks > 0) {
				pendingScreenshotTicks--;
				if (pendingScreenshotTicks == 0) {
					java.io.File targetFile = new java.io.File(pendingScreenshotDir, pendingScreenshotName);
					if (targetFile.exists()) {
						targetFile.delete();
					}
					
					net.minecraft.client.texture.NativeImage image = net.minecraft.client.util.ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
					java.util.concurrent.CompletableFuture.runAsync(() -> {
						try {
							image.writeTo(targetFile);
							client.execute(() -> {
								dev.frost.miniverse.client.gui.map.ThumbnailManager.invalidateAll();
								client.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal("Saved thumbnail."));
								client.options.hudHidden = false;
								if (suspendedScreen != null) {
									client.setScreen(suspendedScreen);
									suspendedScreen = null;
								}
							});
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							image.close();
						}
					});
				}
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				ClientCommandManager.literal("mg")
					.executes(context -> {
						openGui();
						return 1;
					})
			);
			
			dispatcher.register(
				ClientCommandManager.literal("miniverse-dev").then(ClientCommandManager.literal("selector")
					.executes(context -> {
						MinecraftClient client = MinecraftClient.getInstance();
						client.send(() -> {
							RegistrySelectorContext<Block> selectorContext = new RegistrySelectorContext<>(
								"minecraft:block",
								"Select Blocks",
								RegistrySelectorContext.SelectionMode.MULTI,
								new RegistrySelectorState(),
								result -> {},
								"global",
								Set.of()
							);
							client.setScreen(new RegistrySelectorScreen<>(selectorContext, new BlockRegistryProvider()));
						});
						return 1;
					}))
			);
		});

		ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.SESSION_LIST_ID, (payload, context) ->
			context.client().execute(() -> {
				SessionScreen.onServerSnapshot(payload.sessions());
				maybeOpenGui(context.client());
			})
		);

		ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.SYNC_KITS_ID, (payload, context) ->
			context.client().execute(() -> {
				try {
					com.google.gson.JsonArray array = com.google.gson.JsonParser.parseString(payload.jsonArrayString()).getAsJsonArray();
					dev.frost.miniverse.minigame.core.kit.KitRegistry.clear();
					array.forEach(element -> {
						try {
							dev.frost.miniverse.minigame.core.kit.Kit kit = dev.frost.miniverse.minigame.core.kit.Kit.fromJson(
								element.getAsJsonObject(), context.client().world.getRegistryManager()
							);
							dev.frost.miniverse.minigame.core.kit.KitRegistry.register(kit);
						} catch (Exception e) { e.printStackTrace(); }
					});

					if (context.client().currentScreen instanceof dev.frost.miniverse.client.gui.selector.RegistrySelectorScreen<?> rs) {
					    rs.refreshEntries();
					}
				} catch (Exception e) { e.printStackTrace(); }
			})
		);

		ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.CAPTURE_THUMBNAIL_ID, (payload, context) ->
			context.client().execute(() -> {
				MinecraftClient client = context.client();
				if (client.currentScreen != null) {
					suspendedScreen = client.currentScreen;
					client.setScreen(null);
				}
				client.options.hudHidden = true;
				java.nio.file.Path targetPath = java.nio.file.Paths.get(payload.path());
				pendingScreenshotDir = targetPath.getParent().toFile();
				pendingScreenshotName = targetPath.getFileName().toString();
				pendingScreenshotTicks = 20; // Wait 20 ticks (1 second) to ensure frame is redrawn without UI
			})
		);



		ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.MANHUNT_LATE_JOIN_ID, (payload, context) -> {
			context.client().execute(() -> {
				context.client().setScreen(new dev.frost.miniverse.client.gui.ui.ManhuntLateJoinScreen(payload.teammates()));
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.SYNC_BUILDER_SELECTION_ID, (payload, context) ->
			context.client().execute(() -> {
				dev.frost.miniverse.client.gui.map.MapEditorState.INSTANCE.currentBuilderSelection.clear();
				net.minecraft.nbt.NbtList list = payload.selection().getList("regions", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
				for (int i = 0; i < list.size(); i++) {
					net.minecraft.nbt.NbtCompound region = list.getCompound(i);
					net.minecraft.nbt.NbtCompound minNbt = region.getCompound("min");
					net.minecraft.nbt.NbtCompound maxNbt = region.getCompound("max");
					dev.frost.miniverse.client.gui.SessionSnapshotData.EditorPoint min = new dev.frost.miniverse.client.gui.SessionSnapshotData.EditorPoint(
						minNbt.getDouble("x"), minNbt.getDouble("y"), minNbt.getDouble("z"), minNbt.getFloat("yaw"), minNbt.getFloat("pitch")
					);
					dev.frost.miniverse.client.gui.SessionSnapshotData.EditorPoint max = new dev.frost.miniverse.client.gui.SessionSnapshotData.EditorPoint(
						maxNbt.getDouble("x"), maxNbt.getDouble("y"), maxNbt.getDouble("z"), maxNbt.getFloat("yaw"), maxNbt.getFloat("pitch")
					);
					dev.frost.miniverse.client.gui.map.MapEditorState.INSTANCE.currentBuilderSelection.add(
						new dev.frost.miniverse.client.gui.SessionSnapshotData.EditorRegionPart(min, max)
					);
				}
				// Parse placed point positions for the 2D HUD indicator
				dev.frost.miniverse.client.gui.map.MapEditorState.INSTANCE.placementPoints.clear();
				if (payload.selection().contains("points", net.minecraft.nbt.NbtElement.LIST_TYPE)) {
					net.minecraft.nbt.NbtList ptList = payload.selection().getList("points", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
					for (int i = 0; i < ptList.size(); i++) {
						net.minecraft.nbt.NbtCompound pt = ptList.getCompound(i);
						dev.frost.miniverse.client.gui.map.MapEditorState.INSTANCE.placementPoints.add(
							new dev.frost.miniverse.client.gui.SessionSnapshotData.EditorPoint(
								pt.getDouble("x"), pt.getDouble("y"), pt.getDouble("z"),
								pt.getFloat("yaw"), pt.getFloat("pitch")
							)
						);
					}
				}
			})
		);

		ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.FREEZE_STATE_ID, (payload, context) ->
			context.client().execute(() -> ClientFreezeState.setFrozen(payload.frozen()))
		);

		ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.DOWNED_STATE_ID, (payload, context) ->
			context.client().execute(() -> {
				dev.frost.miniverse.minigame.core.freeze.DownedPlayerTracker.setDowned(payload.playerUuid(), payload.downed());
				if (context.client().world != null) {
					net.minecraft.entity.player.PlayerEntity p = context.client().world.getPlayerByUuid(payload.playerUuid());
					if (p != null) {
						p.setPose(payload.downed() ? net.minecraft.entity.EntityPose.SWIMMING : net.minecraft.entity.EntityPose.STANDING);
					}
				}
			})
		);

		ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.MAP_EDITOR_HIDE_ID, (payload, context) ->
			context.client().execute(() -> {
				dev.frost.miniverse.client.gui.map.MapEditorState state = dev.frost.miniverse.client.gui.map.MapEditorState.INSTANCE;
				state.editorActive = false;
				state.currentBuilderSelection.clear();
			})
		);

		ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.HIDE_MAP_EDITOR_OVERLAY_ID, (payload, context) ->
			context.client().execute(() -> {
				dev.frost.miniverse.client.gui.map.MapEditorState.INSTANCE.enabledOverlays.remove(payload.gameId() + ":" + payload.definitionKey());
			})
		);
		ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.SYNC_GAMEMODE_PRESETS_ID, (payload, context) ->
			context.client().execute(() -> {
				net.minecraft.nbt.NbtCompound wrapper = payload.presetsCompound();
				if (wrapper != null && wrapper.contains("list", net.minecraft.nbt.NbtElement.LIST_TYPE)) {
					dev.frost.miniverse.client.gui.SessionSnapshotData.updateGamePresets(payload.gameId(), wrapper.getList("list", net.minecraft.nbt.NbtElement.COMPOUND_TYPE));
				}
				if (context.client().currentScreen instanceof dev.frost.miniverse.client.gui.SessionScreen sessionScreen) {
					if (sessionScreen.getWorkspaceView() instanceof dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView agw) {
						agw.onPresetsUpdated();
					}
				}
			})
		);

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientFreezeState.setFrozen(false);
			dev.frost.miniverse.minigame.core.freeze.DownedPlayerTracker.clear();
			if (client.player != null) {
				client.player.setPose(net.minecraft.entity.EntityPose.STANDING);
			}
			SessionLaunchStatus.clear();
			InventoryLayoutClient.clear();
			ProtectionOverlayClient.clearAll();
			dev.frost.miniverse.client.gui.map.MapEditorState.INSTANCE.clear();
			dev.frost.miniverse.client.gui.SessionSnapshotData.updateEditor(false, java.util.List.of(), null);
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			dev.frost.miniverse.minigame.core.freeze.DownedPlayerTracker.clear();
			if (client.player != null) {
				client.player.setPose(net.minecraft.entity.EntityPose.STANDING);
			}
			sendConnectionHost(client);
		});
	}

	private static void sendConnectionHost(MinecraftClient client) {
		ServerInfo serverInfo = client.getCurrentServerEntry();
		if (serverInfo == null || serverInfo.address == null || serverInfo.address.isBlank()) {
			return;
		}

		String host = extractHost(serverInfo.address);
		if (!host.isBlank() && ClientPlayNetworking.canSend(NetworkConstants.CLIENT_CONNECTION_HOST_ID)) {
			ClientPlayNetworking.send(new NetworkConstants.ClientConnectionHostPayload(host));
		}
	}

	private static String extractHost(String address) {
		String value = address.trim();
		if (value.startsWith("[")) {
			int end = value.indexOf(']');
			return end > 1 ? value.substring(1, end).trim() : "";
		}

		int colon = value.lastIndexOf(':');
		if (colon > 0 && value.indexOf(':') == colon) {
			return value.substring(0, colon).trim();
		}
		return value;
	}

	private static void openGui() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.currentScreen instanceof SessionScreen) {
			return;
		}
		pendingSessionOpen = true;
		ClientPlayNetworking.send(new NetworkConstants.RequestSessionsPayload("open"));
	}

	private static void maybeOpenGui(MinecraftClient client) {
		if (!pendingSessionOpen) {
			return;
		}
		pendingSessionOpen = false;
		if (!(client.currentScreen instanceof SessionScreen)) {
			client.setScreen(new SessionScreen());
		}
	}
}
