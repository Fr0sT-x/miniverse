package dev.frost.miniverse.client.gui.map;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

public class MapEditorRenderIntegration {

    public static Matrix4f lastProjMatrix;
    public static Matrix4f lastModelViewMatrix;

    public static void register() {
        WorldRenderEvents.LAST.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen instanceof MapEditorWorkspaceScreen) {
                Camera camera = context.camera();
                lastProjMatrix = new Matrix4f(context.projectionMatrix());
                lastModelViewMatrix = new Matrix4f(context.positionMatrix());

                // TODO: Render markers
                // TODO: Render Extruded Gizmo
            } else {
                lastProjMatrix = null;
                lastModelViewMatrix = null;
            }
        });
    }
}
