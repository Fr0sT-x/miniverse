package dev.frost.miniverse.minigame.impl.bountyhunt;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.command.BountyHuntCommands;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameMetadata;
import dev.frost.miniverse.session.SessionTopology;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Map;
import java.util.Properties;

public final class BountyHuntDefinition implements MinigameDefinition {
    public static final String ID = "bountyhunt";
    public static final String DISPLAY_NAME = "Bounty Hunt";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public SessionTopology topology() {
        return SessionTopology.SHARED_WORLD;
    }

    @Override
    public MinigameMetadata metadata() {
        return MinigameMetadata.custom(
            this.id(),
            this.displayName(),
            "Track assigned targets and score hits to win.",
            "🎯",
            this.topology()
        );
    }

    @Override
    public void writeSessionProperties(NbtCompound settingsNbt, Properties properties) {
        fillSettingsMap(settingsNbt, properties::setProperty);
    }

    @Override
    public void writeLaunchProperties(NbtCompound settingsNbt, Map<String, String> properties) {
        fillSettingsMap(settingsNbt, (k, v) -> properties.put("miniverse." + k, v));
    }

    private void fillSettingsMap(NbtCompound settingsNbt, java.util.function.BiConsumer<String, String> put) {
        BountyHuntSettings settings = BountyHuntSettings.fromNbt(settingsNbt);
        put.accept("bountyhunt.gracePeriodSeconds", Integer.toString(settings.gracePeriodSeconds()));
        put.accept("bountyhunt.scoreToWin", Integer.toString(settings.scoreToWin()));
        put.accept("bountyhunt.targetSwapIntervalSeconds", Integer.toString(settings.targetSwapIntervalSeconds()));
        put.accept("bountyhunt.trackerEnabled", Boolean.toString(settings.trackerEnabled()));
        put.accept("bountyhunt.netherTracking", Boolean.toString(settings.netherTrackingEnabled()));
        put.accept("bountyhunt.compassCooldownSeconds", Integer.toString(settings.compassCooldownSeconds()));
        put.accept("bountyhunt.trackerItemId", settings.trackerItemId());
        put.accept("bountyhunt.disconnectGraceSeconds", Integer.toString(settings.disconnectGraceSeconds()));
        put.accept("bountyhunt.highValueTargetEnabled", Boolean.toString(settings.highValueTargetEnabled()));
        put.accept("bountyhunt.revengeAssignmentEnabled", Boolean.toString(settings.revengeAssignmentEnabled()));
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        BountyHuntCommands.register(dispatcher);
    }

    @Override
    public void registerEvents() {
        BountyHuntGameEvents.register();
    }
}

