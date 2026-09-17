package dev.frost.miniverse.minigame.impl.zombies.perk;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public enum GlobalPerk {
    INSTANT_KILL("Insta-Kill", Items.SKELETON_SKULL, Formatting.RED, BossBar.Color.RED, 10 * 20),
    MAX_AMMO("Max Ammo", Items.WOODEN_HOE, Formatting.BLUE, BossBar.Color.BLUE, 0),
    DOUBLE_GOLD("Double Gold", Items.GOLD_NUGGET, Formatting.GOLD, BossBar.Color.YELLOW, 30 * 20),
    CARPENTER("Carpenter", Items.OAK_SLAB, Formatting.GREEN, BossBar.Color.GREEN, 0),
    NUKE("Nuke", Items.TNT, Formatting.DARK_RED, BossBar.Color.RED, 0);

    private final String displayName;
    private final Item icon;
    private final Formatting color;
    private final BossBar.Color bossBarColor;
    private final int durationTicks;

    GlobalPerk(String displayName, Item icon, Formatting color, BossBar.Color bossBarColor, int durationTicks) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
        this.bossBarColor = bossBarColor;
        this.durationTicks = durationTicks;
    }

    public String getDisplayName() { return displayName; }
    public Item getIcon() { return icon; }
    public Formatting getColor() { return color; }
    public BossBar.Color getBossBarColor() { return bossBarColor; }
    public int getDurationTicks() { return durationTicks; }
    public boolean isTimed() { return durationTicks > 0; }

    public Text toFormattedText() {
        return Text.literal(this.displayName).formatted(this.color, Formatting.BOLD);
    }
}
