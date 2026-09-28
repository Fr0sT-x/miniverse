package dev.frost.miniverse.client.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.frost.miniverse.chat.ChatChannel;
import dev.frost.miniverse.client.chat.ChannelToggleButton;
import dev.frost.miniverse.client.chat.ChatRoutingClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    protected ChatScreenMixin(Text title) {
        super(title);
    }

    @Shadow
    protected TextFieldWidget chatField;

    @Shadow
    public abstract String normalize(String chatText);

    private ChannelToggleButton miniverse$channelButton;
    private boolean miniverse$hintActive = false;

    @Inject(method = "init", at = @At("TAIL"))
    private void miniverse$onInit(CallbackInfo ci) {
        if (!ChatRoutingClient.isRoutingActive()) {
            return;
        }

        this.chatField.setX(38);
        this.chatField.setWidth(this.width - 40);

        this.miniverse$channelButton = new ChannelToggleButton(2, this.height - 14, 34, 12, button -> {
            ChatRoutingClient.toggleChannel();
            this.miniverse$updateHint(this.chatField.getText());
        });
        this.addDrawableChild(this.miniverse$channelButton);
        this.miniverse$updateHint(this.chatField.getText());
    }

    @Inject(method = "onChatFieldUpdate", at = @At("TAIL"))
    private void miniverse$onChatFieldUpdate(String chatText, CallbackInfo ci) {
        if (!ChatRoutingClient.isRoutingActive()) {
            return;
        }
        this.miniverse$updateHint(chatText);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void miniverse$onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (ChatRoutingClient.isRoutingActive()) {
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                if (this.chatField.getText().trim().isEmpty()) {
                    ChatRoutingClient.toggleChannel();
                    this.miniverse$updateHint("");
                    if (this.client != null) {
                        this.client.getSoundManager().play(
                            PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F)
                        );
                    }
                    cir.setReturnValue(true);
                    cir.cancel();
                }
            }
        }
    }

    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void miniverse$onSendMessage(String chatText, boolean addToHistory, CallbackInfo ci) {
        if (!ChatRoutingClient.isRoutingActive()) {
            return;
        }
        if (chatText.startsWith("/")) {
            return;
        }
        String normalized = this.normalize(chatText);
        if (normalized.isEmpty()) {
            return;
        }
        if (addToHistory && this.client != null) {
            this.client.inGameHud.getChatHud().addToMessageHistory(chatText);
        }
        if (this.client != null && this.client.player != null) {
            if (ChatRoutingClient.getCurrentChannel() == ChatChannel.GLOBAL) {
                this.client.player.networkHandler.sendChatCommand("a " + normalized);
            } else {
                this.client.player.networkHandler.sendChatCommand("t " + normalized);
            }
        }
        ci.cancel();
    }

    private void miniverse$updateHint(String chatText) {
        if (chatText == null || chatText.isEmpty()) {
            String hint = ChatRoutingClient.getCurrentChannel() == ChatChannel.TEAM
                ? "[Team] Type message... (TAB to switch)"
                : "[All] Type message... (TAB to switch)";
            this.chatField.setSuggestion(hint);
            this.miniverse$hintActive = true;
        } else if (this.miniverse$hintActive) {
            this.chatField.setSuggestion(null);
            this.miniverse$hintActive = false;
        }
    }
}
