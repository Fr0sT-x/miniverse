package dev.frost.miniverse.client.mixin;

import dev.frost.miniverse.client.minigame.layout.InventoryLayoutClient;
import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class InventoryScreenMixin extends Screen {

    private boolean pendingResetConfirmation = false;
    private ButtonWidget resetLayoutButton;

    protected InventoryScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void miniverse$addLayoutButtons(CallbackInfo ci) {
        if (!((Object) this instanceof InventoryScreen)) {
            return;
        }
        if (!InventoryLayoutClient.isLayoutSupported()) {
            return;
        }

        HandledScreen<?> handledScreen = (HandledScreen<?>) (Object) this;
        int backgroundWidth = 176; // Default survival inventory width
        int x = Math.max(4, handledScreen.width / 2 - backgroundWidth / 2 - 110 - 4);
        int y = handledScreen.height / 2 - 83 + 4; // 166 is default height, so half is 83

        int buttonWidth = 110;
        int buttonHeight = 20;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save Hotbar Layout"), button -> {
            ClientPlayNetworking.send(new NetworkConstants.SaveLayoutPayload(InventoryLayoutClient.getCurrentGamemode()));
            this.pendingResetConfirmation = false;
            if (this.resetLayoutButton != null) {
                this.resetLayoutButton.setMessage(Text.literal("Reset Layout"));
            }
        }).dimensions(x, y, buttonWidth, buttonHeight).build());

        y += buttonHeight + 4;

        this.resetLayoutButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset Layout"), button -> {
            if (this.pendingResetConfirmation) {
                ClientPlayNetworking.send(new NetworkConstants.ResetLayoutPayload(InventoryLayoutClient.getCurrentGamemode()));
                this.pendingResetConfirmation = false;
                button.setMessage(Text.literal("Reset Layout"));
            } else {
                this.pendingResetConfirmation = true;
                button.setMessage(Text.literal("Confirm Reset"));
            }
        }).dimensions(x, y, buttonWidth, buttonHeight).build());
    }
}
