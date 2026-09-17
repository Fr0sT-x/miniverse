package dev.frost.miniverse.client.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private List<ChatHudLine> messages;
    @Shadow abstract void refresh();

    private static final Pattern COMPACT_SUFFIX_PATTERN = Pattern.compile("^(.*?)(?:\\s*§7\\(x(\\d+)\\)|\\s*\\(x(\\d+)\\))?$");

    private Text miniverse$lastBaseText = null;
    private int miniverse$lastCount = 1;

    private static boolean miniverse$isExternalCompactChatPresent() {
        FabricLoader loader = FabricLoader.getInstance();
        return loader.isModLoaded("compact-chat")
            || loader.isModLoaded("compactchat")
            || loader.isModLoaded("compact_chat")
            || loader.isModLoaded("chatcompact");
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void miniverse$onClear(boolean clearHistory, CallbackInfo ci) {
        this.miniverse$lastBaseText = null;
        this.miniverse$lastCount = 1;
    }

    @Inject(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void miniverse$compactChatMessage(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci) {
        if (miniverse$isExternalCompactChatPresent()) {
            return;
        }

        try {
            if (message == null) return;
            String newStr = message.getString();
            if (newStr == null || newStr.isBlank()) return;

            if (this.messages == null || this.messages.isEmpty()) {
                this.miniverse$lastBaseText = message;
                this.miniverse$lastCount = 1;
                return;
            }

            ChatHudLine top = this.messages.get(0);
            if (top == null || top.content() == null) {
                this.miniverse$lastBaseText = message;
                this.miniverse$lastCount = 1;
                return;
            }

            String topStr = top.content().getString();

            // Check if top message matches new message or already compacted version of it
            boolean matches = false;
            int count = 1;

            if (this.miniverse$lastBaseText != null
                    && Objects.equals(this.miniverse$lastBaseText.getString(), newStr)
                    && Objects.equals(this.miniverse$lastBaseText.getStyle(), message.getStyle())
                    && topStr.startsWith(newStr)) {
                matches = true;
                count = this.miniverse$lastCount + 1;
            } else {
                // Fallback check using regex on top line
                Matcher matcher = COMPACT_SUFFIX_PATTERN.matcher(topStr);
                if (matcher.matches()) {
                    String baseText = matcher.group(1);
                    if (Objects.equals(baseText, newStr)) {
                        matches = true;
                        String countStr = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
                        if (countStr != null) {
                            try {
                                count = Integer.parseInt(countStr) + 1;
                            } catch (NumberFormatException ignored) {
                                count = 2;
                            }
                        } else {
                            count = 2;
                        }
                    }
                }
            }

            if (matches) {
                this.miniverse$lastCount = count;
                Text baseToUse = this.miniverse$lastBaseText != null ? this.miniverse$lastBaseText : message;

                MutableText compacted = Text.empty()
                    .append(baseToUse.copy())
                    .append(Text.literal(" (x" + count + ")").formatted(Formatting.GRAY));

                int ticks = this.client != null && this.client.inGameHud != null ? this.client.inGameHud.getTicks() : top.creationTick();
                ChatHudLine newLine = new ChatHudLine(
                    ticks,
                    compacted,
                    signatureData != null ? signatureData : top.signature(),
                    indicator != null ? indicator : top.indicator()
                );

                this.messages.set(0, newLine);
                this.refresh();
                ci.cancel();
            } else {
                this.miniverse$lastBaseText = message;
                this.miniverse$lastCount = 1;
            }
        } catch (Throwable t) {
            // Failsafe: never crash chat rendering under any circumstance
            this.miniverse$lastBaseText = null;
            this.miniverse$lastCount = 1;
        }
    }
}
