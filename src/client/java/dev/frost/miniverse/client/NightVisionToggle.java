package dev.frost.miniverse.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public final class NightVisionToggle {
	private static final String KEY_TRANSLATION = "key.miniverse.night_vision";
	private static final KeyBinding.Category KEY_CATEGORY = KeyBinding.Category.MISC;
	private static final String MESSAGE_TRANSLATION = "miniverse.night_vision.message";
	private static final String ON_TRANSLATION = "miniverse.night_vision.on";
	private static final String OFF_TRANSLATION = "miniverse.night_vision.off";
	private static boolean enabled;

	private NightVisionToggle() {
	}

	public static void register() {
		KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			KEY_TRANSLATION,
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_N,
			KEY_CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (key.wasPressed()) {
				enabled = !enabled;
				sendToggleMessage(client, enabled);
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> enabled = false);
	}

	public static boolean isEnabled() {
		return enabled;
	}

	private static void sendToggleMessage(MinecraftClient client, boolean enabled) {
		if (client.player == null) {
			return;
		}
		Text status = Text.translatable(enabled ? ON_TRANSLATION : OFF_TRANSLATION)
			.formatted(enabled ? Formatting.GREEN : Formatting.RED);
		client.player.sendMessage(Text.translatable(MESSAGE_TRANSLATION, status), true);
	}
}
