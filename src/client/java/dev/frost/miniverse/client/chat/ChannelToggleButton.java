package dev.frost.miniverse.client.chat;

import dev.frost.miniverse.chat.ChatChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ChannelToggleButton extends ButtonWidget {
    public ChannelToggleButton(int x, int y, int width, int height, PressAction onPress) {
        super(x, y, width, height, Text.empty(), onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        ChatChannel channel = ChatRoutingClient.getCurrentChannel();
        boolean isTeam = channel == ChatChannel.TEAM;
        boolean hovered = this.isHovered();

        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = this.height;

        int bgColor;
        int borderColor;
        Text label;

        if (isTeam) {
            bgColor = hovered ? 0xD0004D66 : 0x90003040;
            borderColor = hovered ? 0xFF00E5FF : 0xFF0099AA;
            label = Text.literal("TEAM").formatted(Formatting.AQUA, Formatting.BOLD);
        } else {
            bgColor = hovered ? 0xD0195E19 : 0x90113B11;
            borderColor = hovered ? 0xFF55FF55 : 0xFF2E992E;
            label = Text.literal("ALL").formatted(Formatting.GREEN, Formatting.BOLD);
        }

        // Fill background
        context.fill(x, y, x + w, y + h, bgColor);

        // 1px border
        context.fill(x, y, x + w, y + 1, borderColor);
        context.fill(x, y + h - 1, x + w, y + h, borderColor);
        context.fill(x, y + 1, x + 1, y + h - 1, borderColor);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, borderColor);

        // Centered label
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        int textX = x + (w - tr.getWidth(label)) / 2;
        int textY = y + (h - 8) / 2;
        context.drawTextWithShadow(tr, label, textX, textY, 0xFFFFFFFF);

        if (hovered) {
            Text tooltip = Text.literal("Click or press [TAB] to switch to " + (isTeam ? "ALL" : "TEAM") + " chat");
            context.drawTooltip(tr, tooltip, mouseX, mouseY);
        }
    }
}
