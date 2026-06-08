package dev.frost.miniverse.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;

public class TestScreenshot {
    public static void test() {
        MinecraftClient client = MinecraftClient.getInstance();
        NativeImage image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
        image.close();
    }
}
