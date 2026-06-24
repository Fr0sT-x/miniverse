package dev.frost.miniverse.client.freeze;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class ClientFreezeHandler {
    private static boolean wasFrozen;

    private ClientFreezeHandler() {
    }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(ClientFreezeHandler::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        if (client.player == null) {
            return;
        }

        boolean frozen = ClientFreezeState.isFrozen();
        if (wasFrozen && !frozen) {
            resyncKey(client, client.options.forwardKey);
            resyncKey(client, client.options.backKey);
            resyncKey(client, client.options.leftKey);
            resyncKey(client, client.options.rightKey);
            resyncKey(client, client.options.jumpKey);
            resyncKey(client, client.options.sprintKey);
            resyncKey(client, client.options.sneakKey);
        }

        wasFrozen = frozen;
        if (!frozen) {
            return;
        }

        suppressKey(client.options.forwardKey);
        suppressKey(client.options.backKey);
        suppressKey(client.options.leftKey);
        suppressKey(client.options.rightKey);
        suppressKey(client.options.jumpKey);
        suppressKey(client.options.sprintKey);
        suppressKey(client.options.sneakKey);

        if (client.player.input != null) {
            clearInput(client.player.input);
        }
    }

    private static void clearInput(Input input) {
        input.movementSideways = 0.0F;
        input.movementForward = 0.0F;
        input.pressingForward = false;
        input.pressingBack = false;
        input.pressingLeft = false;
        input.pressingRight = false;
        input.jumping = false;
        input.sneaking = false;
    }

    private static void suppressKey(KeyBinding key) {
        InputUtil.Key boundKey = InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey());
        KeyBinding.setKeyPressed(boundKey, false);
        key.setPressed(false);
    }

    private static void resyncKey(MinecraftClient client, KeyBinding key) {
        InputUtil.Key boundKey = InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey());
        boolean pressed = false;

        if (boundKey.getCategory() == InputUtil.Type.KEYSYM) {
            pressed = InputUtil.isKeyPressed(client.getWindow().getHandle(), boundKey.getCode());
        } else if (boundKey.getCategory() == InputUtil.Type.MOUSE) {
            pressed = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), boundKey.getCode()) == GLFW.GLFW_PRESS;
        }

        KeyBinding.setKeyPressed(boundKey, pressed);
        key.setPressed(pressed);
    }
}
