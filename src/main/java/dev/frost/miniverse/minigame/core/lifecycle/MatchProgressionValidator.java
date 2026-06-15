package dev.frost.miniverse.minigame.core.lifecycle;

import dev.frost.miniverse.minigame.core.SessionRoster;
import net.minecraft.text.Text;

public interface MatchProgressionValidator {
    ProgressionState checkProgression(SessionRoster roster);
    
    record ProgressionState(boolean blocked, Text chatMessage, Text actionBarMessage) {
        public static ProgressionState valid() {
            return new ProgressionState(false, null, null);
        }
    }
}
