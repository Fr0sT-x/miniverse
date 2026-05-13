package dev.frost.miniverse.minigame.core.lifecycle;

import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public record MatchLifecycleOptions(
    int freezeSeconds,
    int returnSeconds,
    boolean freezeEnabled,
    boolean returnTeleportEnabled,
    Text startTitle,
    Text startSubtitle,
    Text winTitle,
    Text loseTitle,
    Text returnCancelledMessage,
    @Nullable SoundEvent countdownSound,
    @Nullable SoundEvent startSound,
    @Nullable SoundEvent endSound
) {
    public static MatchLifecycleOptions defaults(String minigameName) {
        return new MatchLifecycleOptions(
            10,
            15,
            true,
            true,
            Text.literal(minigameName),
            Text.literal("Starting soon"),
            Text.literal("YOU WIN"),
            Text.literal("YOU LOST"),
            Text.literal("Match return teleport was cancelled by an admin."),
            SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
            SoundEvents.ENTITY_PLAYER_LEVELUP,
            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE
        );
    }

    public MatchLifecycleOptions withFreezeSeconds(int seconds) {
        return new MatchLifecycleOptions(Math.max(0, seconds), this.returnSeconds, this.freezeEnabled,
            this.returnTeleportEnabled, this.startTitle, this.startSubtitle, this.winTitle, this.loseTitle,
            this.returnCancelledMessage, this.countdownSound, this.startSound, this.endSound);
    }

    public MatchLifecycleOptions withReturnSeconds(int seconds) {
        return new MatchLifecycleOptions(this.freezeSeconds, Math.max(0, seconds), this.freezeEnabled,
            this.returnTeleportEnabled, this.startTitle, this.startSubtitle, this.winTitle, this.loseTitle,
            this.returnCancelledMessage, this.countdownSound, this.startSound, this.endSound);
    }

    public MatchLifecycleOptions withFreezeEnabled(boolean enabled) {
        return new MatchLifecycleOptions(this.freezeSeconds, this.returnSeconds, enabled, this.returnTeleportEnabled,
            this.startTitle, this.startSubtitle, this.winTitle, this.loseTitle, this.returnCancelledMessage,
            this.countdownSound, this.startSound, this.endSound);
    }

    public MatchLifecycleOptions withReturnTeleportEnabled(boolean enabled) {
        return new MatchLifecycleOptions(this.freezeSeconds, this.returnSeconds, this.freezeEnabled, enabled,
            this.startTitle, this.startSubtitle, this.winTitle, this.loseTitle, this.returnCancelledMessage,
            this.countdownSound, this.startSound, this.endSound);
    }

    public MatchLifecycleOptions withStartTitle(Text title, Text subtitle) {
        return new MatchLifecycleOptions(this.freezeSeconds, this.returnSeconds, this.freezeEnabled,
            this.returnTeleportEnabled, title, subtitle, this.winTitle, this.loseTitle,
            this.returnCancelledMessage, this.countdownSound, this.startSound, this.endSound);
    }

    public MatchLifecycleOptions withEndTitles(Text winTitle, Text loseTitle) {
        return new MatchLifecycleOptions(this.freezeSeconds, this.returnSeconds, this.freezeEnabled,
            this.returnTeleportEnabled, this.startTitle, this.startSubtitle, winTitle, loseTitle,
            this.returnCancelledMessage, this.countdownSound, this.startSound, this.endSound);
    }
}
