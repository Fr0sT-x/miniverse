package dev.frost.miniverse.minigame.impl.resourcesprint;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public record ResourceSprintSettings(
    Mode mode,
    int timeLimitSeconds,
    TieBreakRule tieBreakRule,
    ObjectiveDistributionMode objectiveDistributionMode,
    List<ObjectiveEntry> objectives
) {
    private static final List<ObjectiveEntry> DEFAULT_OBJECTIVES = List.of(
        new ObjectiveEntry("minecraft:oak_log", ObjectiveDifficulty.EASY, 0.9),
        new ObjectiveEntry("minecraft:crafting_table", ObjectiveDifficulty.EASY, 0.9),
        new ObjectiveEntry("minecraft:iron_ingot", ObjectiveDifficulty.MEDIUM, 0.6),
        new ObjectiveEntry("minecraft:diamond", ObjectiveDifficulty.HARD, 0.3)
    );
    private static final ObjectiveDistributionMode DEFAULT_DISTRIBUTION_MODE = ObjectiveDistributionMode.SHARED;

    public ResourceSprintSettings {
        objectives = List.copyOf(objectives == null ? List.of() : objectives.stream()
            .map(ResourceSprintSettings::normalizeObjectiveEntry)
            .filter(entry -> !entry.id().isBlank())
            .toList());
    }

    public static ResourceSprintSettings defaults() {
        return new ResourceSprintSettings(Mode.FIRST_TO_COMPLETE, 3600, TieBreakRule.SUDDEN_DEATH, DEFAULT_DISTRIBUTION_MODE, DEFAULT_OBJECTIVES);
    }

    public static ResourceSprintSettings fromNbt(NbtCompound nbt) {
        if (nbt == null || nbt.isEmpty()) {
            return defaults();
        }

        Mode mode = parseMode(nbt.getString("mode", Mode.FIRST_TO_COMPLETE.nbtValue()));
        TieBreakRule tieBreakRule = parseTieBreakRule(nbt.getString("tieBreakRule", TieBreakRule.SUDDEN_DEATH.nbtValue()));
        int timeLimitSeconds = Math.max(1, nbt.getInt("timeLimitSeconds").orElse(3600));
        ObjectiveDistributionMode distributionMode = parseDistributionMode(nbt.getString("objectiveDistributionMode", DEFAULT_DISTRIBUTION_MODE.nbtValue()));
        List<ObjectiveEntry> objectives = readObjectives(nbt.getList("objectives").orElseGet(NbtList::new));
        if (objectives.isEmpty()) {
            objectives = DEFAULT_OBJECTIVES;
        }

        return new ResourceSprintSettings(mode, timeLimitSeconds, tieBreakRule, distributionMode, objectives);
    }

    public static ResourceSprintSettings fromProperties(Properties properties) {
        if (properties == null || properties.isEmpty()) {
            return defaults();
        }

        Mode mode = parseMode(properties.getProperty("resourcesprint.mode", Mode.FIRST_TO_COMPLETE.nbtValue()));
        TieBreakRule tieBreakRule = parseTieBreakRule(properties.getProperty("resourcesprint.tieBreakRule", TieBreakRule.SUDDEN_DEATH.nbtValue()));
        int timeLimitSeconds = parseInt(properties.getProperty("resourcesprint.timeLimitSeconds"), 3600);
        ObjectiveDistributionMode distributionMode = parseDistributionMode(properties.getProperty("resourcesprint.objectiveDistributionMode", DEFAULT_DISTRIBUTION_MODE.nbtValue()));
        List<ObjectiveEntry> objectives = readObjectivesFromProperties(properties);

        return new ResourceSprintSettings(mode, Math.max(1, timeLimitSeconds), tieBreakRule, distributionMode, objectives);
    }

    public void writeTo(Properties properties) {
        properties.setProperty("resourcesprint.mode", this.mode.nbtValue());
        properties.setProperty("resourcesprint.timeLimitSeconds", Integer.toString(this.timeLimitSeconds));
        properties.setProperty("resourcesprint.tieBreakRule", this.tieBreakRule.nbtValue());
        properties.setProperty("resourcesprint.objectiveDistributionMode", this.objectiveDistributionMode.nbtValue());
        properties.setProperty("resourcesprint.objectives.count", Integer.toString(this.objectives.size()));
        for (int i = 0; i < this.objectives.size(); i++) {
            ObjectiveEntry objective = this.objectives.get(i);
            properties.setProperty("resourcesprint.objective." + i + ".id", objective.id());
            properties.setProperty("resourcesprint.objective." + i + ".difficulty", objective.difficulty().nbtValue());
            properties.setProperty("resourcesprint.objective." + i + ".probability", Double.toString(objective.probability()));
        }
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("mode", this.mode.nbtValue());
        nbt.putInt("timeLimitSeconds", this.timeLimitSeconds);
        nbt.putString("tieBreakRule", this.tieBreakRule.nbtValue());
        nbt.putString("objectiveDistributionMode", this.objectiveDistributionMode.nbtValue());

        NbtList objectiveList = new NbtList();
        for (ObjectiveEntry objective : this.objectives) {
            NbtCompound entry = new NbtCompound();
            entry.putString("id", objective.id());
            entry.putString("difficulty", objective.difficulty().nbtValue());
            entry.putDouble("probability", objective.probability());
            objectiveList.add(entry);
        }
        nbt.put("objectives", objectiveList);
        nbt.putInt("objectiveCount", this.objectives.size());
        return nbt;
    }

    private static ObjectiveDistributionMode parseDistributionMode(String value) {
        try {
            return ObjectiveDistributionMode.valueOf(normalizeEnum(value));
        } catch (IllegalArgumentException ignored) {
            return DEFAULT_DISTRIBUTION_MODE;
        }
    }

    private static Mode parseMode(String value) {
        try {
            return Mode.valueOf(normalizeEnum(value));
        } catch (IllegalArgumentException ignored) {
            return Mode.FIRST_TO_COMPLETE;
        }
    }

    private static TieBreakRule parseTieBreakRule(String value) {
        try {
            return TieBreakRule.valueOf(normalizeEnum(value));
        } catch (IllegalArgumentException ignored) {
            return TieBreakRule.SUDDEN_DEATH;
        }
    }

    private static String normalizeEnum(String value) {
        return value == null ? "" : value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private static int parseInt(String value, int fallback) {
        try {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static List<ObjectiveEntry> readObjectives(NbtList list) {
        List<ObjectiveEntry> objectives = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound compound = list.getCompoundOrEmpty(i);
            String objective = compound.getString("id", "").trim().toLowerCase(Locale.ROOT);
            if (!objective.isBlank()) {
                ObjectiveDifficulty difficulty = ObjectiveDifficulty.fromString(compound.getString("difficulty", ObjectiveDifficulty.EASY.nbtValue()));
                double probability = compound.getDouble("probability").filter(value -> value > 0).orElse(1.0);
                objectives.add(new ObjectiveEntry(objective, difficulty, probability));
            }
        }
        return objectives;
    }

    private static List<ObjectiveEntry> readObjectivesFromProperties(Properties properties) {
        int count = parseInt(properties.getProperty("resourcesprint.objectives.count"), -1);
        List<ObjectiveEntry> objectives = new ArrayList<>();

        if (count >= 0) {
            for (int i = 0; i < count; i++) {
                String id = properties.getProperty("resourcesprint.objective." + i + ".id", "").trim().toLowerCase(Locale.ROOT);
                if (id.isBlank()) {
                    continue;
                }
                ObjectiveDifficulty difficulty = ObjectiveDifficulty.fromString(properties.getProperty("resourcesprint.objective." + i + ".difficulty", ObjectiveDifficulty.EASY.nbtValue()));
                double probability = parseDouble(properties.getProperty("resourcesprint.objective." + i + ".probability"), 1.0);
                objectives.add(new ObjectiveEntry(id, difficulty, probability));
            }
        }

        if (objectives.isEmpty()) {
            String legacy = properties.getProperty("resourcesprint.objectives", "");
            if (!legacy.isBlank()) {
                for (String part : legacy.split("[\\n,]")) {
                    String objective = part.trim().toLowerCase(Locale.ROOT);
                    if (!objective.isBlank()) {
                        objectives.add(new ObjectiveEntry(objective, ObjectiveDifficulty.EASY, 1.0));
                    }
                }
            }
        }

        if (objectives.isEmpty()) {
            objectives = DEFAULT_OBJECTIVES;
        }

        return objectives;
    }

    private static ObjectiveEntry normalizeObjectiveEntry(ObjectiveEntry entry) {
        if (entry == null) {
            return new ObjectiveEntry("", ObjectiveDifficulty.EASY, 1.0);
        }
        return new ObjectiveEntry(entry.id() == null ? "" : entry.id().trim().toLowerCase(Locale.ROOT), entry.difficulty() == null ? ObjectiveDifficulty.EASY : entry.difficulty(), entry.probability() > 0 ? entry.probability() : 1.0);
    }

    public enum Mode {
        FIRST_TO_COMPLETE,
        TIME_LIMITED;

        public String nbtValue() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public enum TieBreakRule {
        SUDDEN_DEATH,
        FASTEST_TOTAL_TIME;

        public String nbtValue() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public enum ObjectiveDistributionMode {
        SHARED("Shared - All teams get the same objectives"),
        PROBABILISTIC("Probabilistic - Each team gets different objectives based on difficulty probabilities");

        private final String label;

        ObjectiveDistributionMode(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }

        public ObjectiveDistributionMode next() {
            return this == SHARED ? PROBABILISTIC : SHARED;
        }

        public String nbtValue() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public enum ObjectiveDifficulty {
        EASY,
        MEDIUM,
        HARD;

        public String nbtValue() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public static ObjectiveDifficulty fromString(String value) {
            try {
                return ObjectiveDifficulty.valueOf(normalizeEnum(value));
            } catch (IllegalArgumentException ignored) {
                return EASY;
            }
        }
    }

    public record ObjectiveEntry(String id, ObjectiveDifficulty difficulty, double probability) {
    }

    private static double parseDouble(String value, double fallback) {
        try {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            double d = Double.parseDouble(value.trim());
            return d > 0 && d <= 1.0 ? d : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}



