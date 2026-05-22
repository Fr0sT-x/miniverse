package dev.frost.miniverse.session.plan;

import dev.frost.miniverse.session.SeedPlan;
import dev.frost.miniverse.session.SessionGameDescriptor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SessionPlan {
    private final String requestedGame;
    private final String sessionName;
    private final boolean explicitPlan;
    private final String plannedGame;
    private final boolean autoLaunch;
    private final NbtCompound settings;
    private final List<TeamPlan> teams;
    private final SeedPlan seedPlan;
    private final String validationError;

    private SessionPlan(
        String requestedGame,
        String sessionName,
        boolean explicitPlan,
        String plannedGame,
        boolean autoLaunch,
        NbtCompound settings,
        List<TeamPlan> teams,
        SeedPlan seedPlan,
        String validationError
    ) {
        this.requestedGame = requestedGame == null ? "" : requestedGame;
        this.sessionName = sessionName == null ? "" : sessionName;
        this.explicitPlan = explicitPlan;
        this.plannedGame = plannedGame == null ? this.requestedGame : plannedGame;
        this.autoLaunch = autoLaunch;
        this.settings = settings == null ? new NbtCompound() : settings.copy();
        this.teams = teams == null ? List.of() : List.copyOf(teams);
        this.seedPlan = seedPlan;
        this.validationError = validationError;
    }

    public static SessionPlan fromPayload(String requestedGame, String sessionName, NbtCompound payloadPlan) {
        if (payloadPlan == null || payloadPlan.isEmpty()) {
            return new SessionPlan(
                requestedGame,
                sessionName,
                false,
                requestedGame,
                false,
                new NbtCompound(),
                List.of(),
                SeedPlan.randomSameSeed(),
                null
            );
        }

        NbtCompound settings = payloadPlan.contains("settings", NbtElement.COMPOUND_TYPE)
            ? payloadPlan.getCompound("settings")
            : new NbtCompound();
        String plannedGame = getStringOrDefault(payloadPlan, "game", requestedGame);
        boolean autoLaunch = getBooleanOrDefault(payloadPlan, "launch", false);

        SeedPlan seedPlan = resolveSeedPlan(settings);
        String validationError = seedPlan == null ? "Fixed seed was selected but no valid seed value was provided." : null;

        List<TeamPlan> teams = new ArrayList<>();
        NbtList groupList = payloadPlan.getList("groups", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < groupList.size(); i++) {
            TeamPlan team = TeamPlan.fromNbt(groupList.getCompound(i), "Team " + (i + 1));
            if (!team.isEmpty()) {
                teams.add(team);
            }
        }

        String resolvedName = getStringOrDefault(payloadPlan, "name", sessionName);
        return new SessionPlan(
            requestedGame,
            resolvedName,
            true,
            plannedGame,
            autoLaunch,
            settings,
            teams,
            seedPlan,
            validationError
        );
    }

    public String requestedGame() {
        return this.requestedGame;
    }

    public String sessionName() {
        return this.sessionName;
    }

    public boolean explicitPlan() {
        return this.explicitPlan;
    }

    public String plannedGame() {
        return this.plannedGame;
    }

    public boolean autoLaunch() {
        return this.autoLaunch;
    }

    public NbtCompound settings() {
        return this.settings.copy();
    }

    public List<TeamPlan> teams() {
        return this.teams;
    }

    public Optional<SeedPlan> seedPlan() {
        return Optional.ofNullable(this.seedPlan);
    }

    public Optional<String> validationError() {
        return Optional.ofNullable(this.validationError);
    }

    public boolean matches(SessionGameDescriptor gameType) {
        return gameType != null && gameType.getCommandName().equalsIgnoreCase(this.plannedGame);
    }

    public boolean shouldAssignAllOnlinePlayers(SessionGameDescriptor gameType) {
        return this.explicitPlan && this.matches(gameType) && this.teams.isEmpty();
    }

    public NbtCompound settingsWithTeamRoles() {
        NbtCompound copy = this.settings.copy();
        NbtList roles = new NbtList();
        NbtList existing = copy.getList("roles", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < existing.size(); i++) {
            roles.add(existing.getCompound(i).copy());
        }

        for (TeamPlan team : this.teams) {
            for (PlayerRole role : team.roles()) {
                roles.add(role.toNbt());
            }
        }

        if (!roles.isEmpty()) {
            copy.put("roles", roles);
        }
        return copy;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("game", this.plannedGame);
        nbt.putString("name", this.sessionName);
        nbt.putBoolean("launch", this.autoLaunch);
        nbt.put("settings", this.settings.copy());

        NbtList groups = new NbtList();
        for (TeamPlan team : this.teams) {
            groups.add(team.toNbt());
        }
        nbt.put("groups", groups);
        return nbt;
    }

    private static SeedPlan resolveSeedPlan(NbtCompound settings) {
        String seedMode = getStringOrDefault(settings, "seedMode", "random");
        if (!"fixed".equalsIgnoreCase(seedMode)) {
            return SeedPlan.randomSameSeed();
        }

        return settings.contains("seed", NbtElement.NUMBER_TYPE)
            ? SeedPlan.fixed(settings.getLong("seed"))
            : null;
    }

    private static String getStringOrDefault(NbtCompound nbt, String key, String fallback) {
        return nbt != null && nbt.contains(key, NbtElement.STRING_TYPE)
            ? nbt.getString(key)
            : fallback;
    }

    private static boolean getBooleanOrDefault(NbtCompound nbt, String key, boolean fallback) {
        return nbt != null && nbt.contains(key, NbtElement.NUMBER_TYPE)
            ? nbt.getBoolean(key)
            : fallback;
    }
}
