package dev.frost.miniverse.minigame.core.util;

import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamOptions;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class VanillaTeamSync {

    private VanillaTeamSync() {}

    public static VanillaTeamOptions roleOptions(String prefixText, Formatting color, boolean friendlyFire) {
        return VanillaTeamOptions.defaults()
            .withColor(color)
            .withPrefix(Text.literal("[" + prefixText.toUpperCase() + "] ").formatted(color))
            .withFriendlyFireAllowed(friendlyFire)
            .withCollisionRule(AbstractTeam.CollisionRule.NEVER);
    }

    public static VanillaTeamOptions deadOptions() {
        return VanillaTeamOptions.defaults()
            .withColor(Formatting.GRAY)
            .withFriendlyFireAllowed(false)
            .withCollisionRule(AbstractTeam.CollisionRule.NEVER);
    }
    
    public static VanillaTeamOptions aliveOptionsTemplate() {
        return VanillaTeamOptions.defaults()
            .withFriendlyFireAllowed(true)
            .withCollisionRule(AbstractTeam.CollisionRule.NEVER);
    }
}
