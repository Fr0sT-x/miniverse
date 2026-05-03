package dev.frost.miniverse.client;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class MiniverseClient implements ClientModInitializer {
	public static KeyBinding OPEN_GUI_KEY;

	@Override
	public void onInitializeClient() {
		NetworkConstants.registerPayloadTypes();

		OPEN_GUI_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.miniverse.open_gui",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_RIGHT_SHIFT,
			KeyBinding.Category.create(Identifier.of(NetworkConstants.MOD_ID, "miniverse"))
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_GUI_KEY.wasPressed()) {
				openGui();
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
			ClientCommandManager.literal("mg")
				.executes(context -> {
					openGui();
					return 1;
				})
		));

		ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.SESSION_LIST_ID, (payload, context) ->
			context.client().execute(() -> SessionScreen.onServerSnapshot(payload.sessions()))
		);
	}

	private static void openGui() {
		MinecraftClient client = MinecraftClient.getInstance();
		client.setScreen(new SessionScreen());
	}
}