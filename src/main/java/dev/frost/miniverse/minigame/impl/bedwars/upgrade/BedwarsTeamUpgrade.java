package dev.frost.miniverse.minigame.impl.bedwars.upgrade;

import dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsCurrency;

public enum BedwarsTeamUpgrade {
    SHARPNESS  ("Sharpness",   BedwarsCurrency.DIAMOND, new int[]{4},             "Sharpness I on all team swords."),
    PROTECTION ("Protection",  BedwarsCurrency.DIAMOND, new int[]{4, 8},          "Protection I/II on team armour."),
    FORGE      ("Forge",       BedwarsCurrency.DIAMOND, new int[]{4, 4, 4, 8},    "Speeds island generators."),
    HASTE      ("Haste",       BedwarsCurrency.DIAMOND, new int[]{2, 4},          "Haste I/II for team members."),
    HEAL_POOL  ("Heal Pool",   BedwarsCurrency.DIAMOND, new int[]{1},             "Regen I near team bed."),
    DRAGON_BUFF("Dragon Buff", BedwarsCurrency.EMERALD, new int[]{5},             "Dragon deals more damage in Sudden Death.");

    private final String displayName;
    private final BedwarsCurrency currency;
    private final int[] tierCosts;
    private final String description;

    BedwarsTeamUpgrade(String displayName, BedwarsCurrency currency, int[] tierCosts, String description) {
        this.displayName = displayName;
        this.currency = currency;
        this.tierCosts = tierCosts;
        this.description = description;
    }

    public String displayName() { return displayName; }
    public BedwarsCurrency currency() { return currency; }
    public int[] tierCosts() { return tierCosts; }
    public String description() { return description; }
}
