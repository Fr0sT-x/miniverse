package dev.frost.miniverse.client.gui.ui;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.Map;
import java.util.UUID;

public class ManhuntLateJoinScreen extends AbstractPopupScreen {
    private final Map<UUID, String> teammates;

    public ManhuntLateJoinScreen(Map<UUID, String> teammates) {
        super(null, Text.literal("Choose Teammate to Teleport"), 240, 50 + (teammates.size() * 25));
        this.teammates = teammates;
    }

    @Override
    protected void initPopup() {
        int y = this.popupY + 30;
        for (Map.Entry<UUID, String> entry : this.teammates.entrySet()) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Teleport to " + entry.getValue()), btn -> {
                if (this.client != null && this.client.getNetworkHandler() != null) {
                    this.client.getNetworkHandler().sendCommand("manhunt _latejoin_tp " + entry.getValue());
                }
                this.close();
            }).dimensions(this.popupX + 20, y, 200, 20).build());
            y += 25;
        }
    }
}
