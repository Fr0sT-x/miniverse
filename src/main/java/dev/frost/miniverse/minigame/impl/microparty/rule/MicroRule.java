package dev.frost.miniverse.minigame.impl.microparty.rule;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMapConfig;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public interface MicroRule {
    /**
     * Unique identifier for this rule.
     */
    String id();

    /**
     * User-friendly display name for workspace setup menus (e.g. "Anvil Dodge").
     */
    default String name() {
        return id();
    }

    /**
     * Descriptive summary of this microgame's objective for workspace setup menus.
     */
    default String description() {
        return instruction().getString();
    }

    /**
     * Short, punchy headline title displayed as Title on screen (e.g. "STATUE!").
     */
    Text title();

    /**
     * Instructional subtitle displayed underneath (e.g. "Don't move a muscle!").
     */
    Text instruction();

    /**
     * Base duration in seconds at standard 1.0x speed tier.
     */
    int baseDurationSeconds();

    /**
     * Context-aware title displayed on screen. Defaults to static title().
     */
    default Text title(MicroPartyMinigame game) {
        return title();
    }

    /**
     * Context-aware subtitle displayed underneath. Rules with dynamic counts (e.g. jump count)
     * can override this to reflect scaled requirements. Defaults to static instruction().
     */
    default Text instruction(MicroPartyMinigame game) {
        return instruction();
    }

    /**
     * Duration in ticks for this micro-rule, scaled dynamically by the game's current speed factor.
     */
    default int getDurationTicks(MicroPartyMinigame game) {
        float factor = game != null ? game.getSpeedFactor() : 1.0f;
        int baseSecs = baseDurationSeconds();
        return Math.max(40, Math.round(baseSecs * 20 * factor)); // minimum 2.0s (40 ticks)
    }

    /**
     * Duration in seconds for this micro-rule.
     */
    default int getDurationSeconds(MicroPartyMinigame game) {
        return Math.max(2, (getDurationTicks(game) + 10) / 20);
    }

    /**
     * Whether this rule can run on the provided map configuration.
     * Rules requiring color zones or high ground can check mapConfig here.
     */
    default boolean isApplicable(MicroPartyMapConfig mapConfig) {
        return true;
    }

    /**
     * Called when the rule is selected for announcement (before title and instruction are sent).
     * Rules that randomize target words, phrases, equations, or modes should initialize them here.
     */
    default void onPrepare(MicroPartyMinigame game, MinecraftServer server) {}

    /**
     * Called when the micro-rule round begins (announcement displayed, players unfrozen).
     */
    void onStart(MicroPartyMinigame game, MinecraftServer server);

    /**
     * Called every server tick while this micro-rule is active.
     */
    default void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {}

    /**
     * Evaluates whether the given player has successfully passed or is currently meeting the pass condition.
     */
    boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game);

    /**
     * Hook when a player takes damage during this rule.
     * Returns true to allow damage, false to cancel.
     */
    default boolean onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount, MicroPartyMinigame game) {
        return false;
    }

    /**
     * Hook when an entity takes damage during this rule.
     */
    default boolean onEntityDamage(net.minecraft.entity.LivingEntity entity, DamageSource source, float amount, MicroPartyMinigame game) {
        return true;
    }

    /**
     * Hook when a player attacks another entity/player during this rule.
     */
    default void onPlayerAttack(ServerPlayerEntity attacker, Entity target, MicroPartyMinigame game) {}

    /**
     * Hook when a player sends a chat message during this rule.
     * Returns true to consume/suppress the message silently, false to allow normal broadcast.
     */
    default boolean onChatMessage(ServerPlayerEntity player, String message, MicroPartyMinigame game) {
        return false;
    }

    /**
     * Hook when a player interacts with a block during this rule.
     */
    default net.minecraft.util.ActionResult onUseBlock(ServerPlayerEntity player, net.minecraft.world.World world, net.minecraft.util.Hand hand, net.minecraft.util.hit.BlockHitResult hitResult, MicroPartyMinigame game) {
        return net.minecraft.util.ActionResult.PASS;
    }

    /**
     * Called when the micro-rule round ends (before resolving results & clearing entities).
     */
    void onEnd(MicroPartyMinigame game, MinecraftServer server);
}
