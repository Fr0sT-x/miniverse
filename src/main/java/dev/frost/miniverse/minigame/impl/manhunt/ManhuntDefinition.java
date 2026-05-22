package dev.frost.miniverse.minigame.impl.manhunt;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.command.ManhuntCommands;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameMetadata;
import dev.frost.miniverse.session.SessionTopology;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Map;
import java.util.Properties;

public final class ManhuntDefinition implements MinigameDefinition {
    public static final String ID = "manhunt";
    public static final String DISPLAY_NAME = "Manhunt";

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
            "One player hunts while others speedrun.",
            "⚔",
            this.topology()
        );
    }

    @Override
    public void writeSessionProperties(NbtCompound settingsNbt, Properties properties) {
        ManhuntSettings settings = ManhuntSettings.fromNbt(settingsNbt);
        properties.setProperty("manhunt.hunterReleaseDelaySeconds", Integer.toString(settings.hunterReleaseDelaySeconds()));
        properties.setProperty("manhunt.speedrunnerRespawnDelaySeconds", Integer.toString(settings.speedrunnerRespawnDelaySeconds()));
        properties.setProperty("manhunt.huntersCompass", Boolean.toString(settings.huntersCompassEnabled()));
        properties.setProperty("manhunt.netherTracking", Boolean.toString(settings.netherTrackingEnabled()));
        properties.setProperty("manhunt.compassCooldownSeconds", Integer.toString(settings.compassCooldownSeconds()));
        properties.setProperty("manhunt.runnerGlowPulseMinutes", Integer.toString(settings.runnerGlowPulseMinutes()));
        properties.setProperty("manhunt.runnerLives", Integer.toString(settings.runnerLives()));
        properties.setProperty("manhunt.hunterLives", Integer.toString(settings.hunterLives()));
        properties.setProperty("manhunt.hunterRespawnDelaySeconds", Integer.toString(settings.hunterRespawnDelaySeconds()));
        properties.setProperty("manhunt.disconnectGraceSeconds", Integer.toString(settings.disconnectGraceSeconds()));

        NbtList roles = settingsNbt.getList("roles").orElseGet(NbtList::new);
        for (int i = 0; i < roles.size(); i++) {
            NbtCompound role = roles.getCompoundOrEmpty(i);
            String uuid = role.getString("uuid", "");
            String roleName = role.getString("role", "");
            if (!uuid.isBlank() && !roleName.isBlank()) {
                properties.setProperty("manhunt.role." + uuid, roleName);
            }
        }
    }

    @Override
    public void writeLaunchProperties(NbtCompound settingsNbt, Map<String, String> properties) {
        ManhuntSettings settings = ManhuntSettings.fromNbt(settingsNbt);
        properties.put("miniverse.manhunt.hunterReleaseDelaySeconds", Integer.toString(settings.hunterReleaseDelaySeconds()));
        properties.put("miniverse.manhunt.respawnDelaySeconds", Integer.toString(settings.speedrunnerRespawnDelaySeconds()));
        properties.put("miniverse.manhunt.huntersCompass", Boolean.toString(settings.huntersCompassEnabled()));
        properties.put("miniverse.manhunt.netherTracking", Boolean.toString(settings.netherTrackingEnabled()));
        properties.put("miniverse.manhunt.compassCooldownSeconds", Integer.toString(settings.compassCooldownSeconds()));
        properties.put("miniverse.manhunt.runnerGlowPulseMinutes", Integer.toString(settings.runnerGlowPulseMinutes()));
        properties.put("miniverse.manhunt.runnerLives", Integer.toString(settings.runnerLives()));
        properties.put("miniverse.manhunt.hunterLives", Integer.toString(settings.hunterLives()));
        properties.put("miniverse.manhunt.hunterRespawnDelaySeconds", Integer.toString(settings.hunterRespawnDelaySeconds()));
        properties.put("miniverse.manhunt.disconnectGraceSeconds", Integer.toString(settings.disconnectGraceSeconds()));
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        ManhuntCommands.register(dispatcher);
    }

    @Override
    public void registerEvents() {
        ManhuntGameEvents.register();
    }
}
