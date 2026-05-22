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
        BountyHuntSettings settings = BountyHuntSettings.fromNbt(settingsNbt);
        properties.setProperty("bountyhunt.gracePeriodSeconds", Integer.toString(settings.gracePeriodSeconds()));
        properties.setProperty("bountyhunt.respawnInvincibilitySeconds", Integer.toString(settings.respawnInvincibilitySeconds()));
        properties.setProperty("bountyhunt.scoreToWin", Integer.toString(settings.scoreToWin()));
        properties.setProperty("bountyhunt.targetSwapIntervalSeconds", Integer.toString(settings.targetSwapIntervalSeconds()));
        properties.setProperty("bountyhunt.trackerEnabled", Boolean.toString(settings.trackerEnabled()));
        properties.setProperty("bountyhunt.netherTracking", Boolean.toString(settings.netherTrackingEnabled()));
        properties.setProperty("bountyhunt.compassCooldownSeconds", Integer.toString(settings.compassCooldownSeconds()));
        properties.setProperty("bountyhunt.trackerItemId", settings.trackerItemId());
        properties.setProperty("bountyhunt.disconnectGraceSeconds", Integer.toString(settings.disconnectGraceSeconds()));
    }

    @Override
    public void writeLaunchProperties(NbtCompound settingsNbt, Map<String, String> properties) {
        BountyHuntSettings settings = BountyHuntSettings.fromNbt(settingsNbt);
        properties.put("miniverse.bountyhunt.gracePeriodSeconds", Integer.toString(settings.gracePeriodSeconds()));
        properties.put("miniverse.bountyhunt.respawnInvincibilitySeconds", Integer.toString(settings.respawnInvincibilitySeconds()));
        properties.put("miniverse.bountyhunt.scoreToWin", Integer.toString(settings.scoreToWin()));
        properties.put("miniverse.bountyhunt.targetSwapIntervalSeconds", Integer.toString(settings.targetSwapIntervalSeconds()));
        properties.put("miniverse.bountyhunt.trackerEnabled", Boolean.toString(settings.trackerEnabled()));
        properties.put("miniverse.bountyhunt.netherTracking", Boolean.toString(settings.netherTrackingEnabled()));
        properties.put("miniverse.bountyhunt.compassCooldownSeconds", Integer.toString(settings.compassCooldownSeconds()));
        properties.put("miniverse.bountyhunt.trackerItemId", settings.trackerItemId());
        properties.put("miniverse.bountyhunt.disconnectGraceSeconds", Integer.toString(settings.disconnectGraceSeconds()));
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

