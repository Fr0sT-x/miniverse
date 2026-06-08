package dev.frost.miniverse.minigame.core.vanilla;

import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

public record VanillaTeamOptions(
    Formatting color,
    Text prefix,
    Text suffix,
    boolean friendlyFireAllowed,
    AbstractTeam.CollisionRule collisionRule,
    AbstractTeam.VisibilityRule nameTagVisibility,
    AbstractTeam.VisibilityRule deathMessageVisibility,
    boolean showFriendlyInvisibles
) {
    public static VanillaTeamOptions defaults() {
        return new VanillaTeamOptions(
            Formatting.WHITE,
            Text.empty(),
            Text.empty(),
            true,
            AbstractTeam.CollisionRule.NEVER,
            AbstractTeam.VisibilityRule.ALWAYS,
            AbstractTeam.VisibilityRule.ALWAYS,
            true
        );
    }

    public VanillaTeamOptions withColor(Formatting color) {
        return new VanillaTeamOptions(color, this.prefix, this.suffix, this.friendlyFireAllowed, this.collisionRule, this.nameTagVisibility, this.deathMessageVisibility, this.showFriendlyInvisibles);
    }

    public VanillaTeamOptions withPrefix(@Nullable Text prefix) {
        return new VanillaTeamOptions(this.color, prefix == null ? Text.empty() : prefix, this.suffix, this.friendlyFireAllowed, this.collisionRule, this.nameTagVisibility, this.deathMessageVisibility, this.showFriendlyInvisibles);
    }

    public VanillaTeamOptions withSuffix(@Nullable Text suffix) {
        return new VanillaTeamOptions(this.color, this.prefix, suffix == null ? Text.empty() : suffix, this.friendlyFireAllowed, this.collisionRule, this.nameTagVisibility, this.deathMessageVisibility, this.showFriendlyInvisibles);
    }

    public VanillaTeamOptions withFriendlyFireAllowed(boolean friendlyFireAllowed) {
        return new VanillaTeamOptions(this.color, this.prefix, this.suffix, friendlyFireAllowed, this.collisionRule, this.nameTagVisibility, this.deathMessageVisibility, this.showFriendlyInvisibles);
    }

    public VanillaTeamOptions withCollisionRule(AbstractTeam.CollisionRule collisionRule) {
        return new VanillaTeamOptions(this.color, this.prefix, this.suffix, this.friendlyFireAllowed, collisionRule, this.nameTagVisibility, this.deathMessageVisibility, this.showFriendlyInvisibles);
    }

    public VanillaTeamOptions withNameTagVisibility(AbstractTeam.VisibilityRule nameTagVisibility) {
        return new VanillaTeamOptions(this.color, this.prefix, this.suffix, this.friendlyFireAllowed, this.collisionRule, nameTagVisibility, this.deathMessageVisibility, this.showFriendlyInvisibles);
    }

    public VanillaTeamOptions withShowFriendlyInvisibles(boolean showFriendlyInvisibles) {
        return new VanillaTeamOptions(this.color, this.prefix, this.suffix, this.friendlyFireAllowed, this.collisionRule, this.nameTagVisibility, this.deathMessageVisibility, showFriendlyInvisibles);
    }
}
