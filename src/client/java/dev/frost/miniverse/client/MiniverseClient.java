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

		OPEN_GUI_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.miniverse.open_gui",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_RIGHT_SHIFT,
			"category." + NetworkConstants.MOD_ID + ".miniverse"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_GUI_KEY.wasPressed()) {
				openGui();
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
			})
		);

		ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.FREEZE_STATE_ID, (payload, context) ->
			context.client().execute(() -> ClientFreezeState.setFrozen(payload.frozen()))
		);

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientFreezeState.setFrozen(false);
			SessionLaunchStatus.clear();
			InventoryLayoutClient.clear();
			ProtectionOverlayClient.clearAll();
			dev.frost.miniverse.client.gui.map.MapEditorState.INSTANCE.clear();
			dev.frost.miniverse.client.gui.SessionSnapshotData.updateEditor(false, java.util.List.of(), null);
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> sendConnectionHost(client));
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
