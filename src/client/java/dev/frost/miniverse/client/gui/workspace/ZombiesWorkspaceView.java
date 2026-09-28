package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.minigame.impl.zombies.ZombiesDefinition;
import dev.frost.miniverse.minigame.impl.zombies.ZombiesDifficulty;
import dev.frost.miniverse.minigame.impl.zombies.ZombiesDifficultyConfig;
import dev.frost.miniverse.minigame.impl.zombies.ZombiesDifficultyConfig.DifficultyEntry;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponCustomConfig;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponType;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ZombiesWorkspaceView extends AbstractGamemodeWorkspaceView {
    public enum TuningSubTab {
        WEAPONS,
        DIFFICULTY
    }

    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private IntFieldWidget startGoldField;
    private IntFieldWidget maxRoundsField;
    private IntFieldWidget intermissionField;
    private IntFieldWidget bleedoutField;
    private ButtonWidget friendlyFireButton;
    private ButtonWidget difficultyButton;
    private ButtonWidget endlessModeButton;

    private int startGold = 500;
    private int maxRounds = 30;
    private int intermissionSeconds = 10;
    private int bleedoutSeconds = 30;
    private boolean friendlyFire = false;
    private boolean endlessMode = false;
    private ZombiesDifficulty difficulty = ZombiesDifficulty.EASY;

    // Tuning state
    private final WeaponCustomConfig weaponConfig = WeaponCustomConfig.defaults();
    private final ZombiesDifficultyConfig difficultyConfig = ZombiesDifficultyConfig.defaults();
    private TuningSubTab activeTuningSubTab = TuningSubTab.WEAPONS;
    private ZombiesDifficulty activeDifficultyTuning = ZombiesDifficulty.EASY;
    private int weaponScrollOffset = 0;
    private SessionScreen screenRef;

    // Weapon row widgets
    private record WeaponRowWidgets(
        WeaponType type,
        ButtonWidget chestToggle,
        TextFieldWidget damageField,
        TextFieldWidget reloadField
    ) {}
    private final List<WeaponRowWidgets> activeWeaponRowWidgets = new ArrayList<>();

    // Difficulty widgets
    private TextFieldWidget diffHealthField;
    private TextFieldWidget diffDamageField;
    private TextFieldWidget diffSpeedField;
    private TextFieldWidget diffWinBreakField;
    private TextFieldWidget diffSpecialField;

    private IntFieldWidget diffBaseMobsField;
    private IntFieldWidget diffMobsPerRoundField;
    private IntFieldWidget diffMaxActiveField;
    private IntFieldWidget diffSpawnMinField;
    private IntFieldWidget diffSpawnRndField;

    public ZombiesWorkspaceView() {
        super(ZombiesDefinition.ID);
        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Survivors", UiTheme.ACCENT_RED, false);
        this.useRosterGrid(this.playerGrid, "players", "P", "Survivors", "Setup", "Select participating survivors.", UiTheme.ACCENT_RED);
        this.useMapSelection("map", "M", "Map Selection", "Setup", "Choose a validated Zombies map.", UiTheme.ACCENT_BLUE, "Valid Zombies Maps");
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Configure gold, round limits, and bleedout timers.", UiTheme.ACCENT_BLUE);
        this.moduleManager.register("tuning", "T", "Balance & Tuning", "Tuning", "Configure weapon stats, lucky chest drops, and difficulty scaling.", UiTheme.ACCENT_GREEN);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        this.screenRef = screen;
        this.activeWeaponRowWidgets.clear();

        if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            this.rulesLayout.addRow(
                "Difficulty", (s, x, y, w) -> {
                    this.difficultyButton = this.addCycleButton(s,
                        () -> "Difficulty: " + this.difficulty.getDisplayName(),
                        () -> this.difficulty.ordinal(),
                        x, y, w,
                        new String[] {
                            "Easy: Baseline zombie health and damage. Standard wave pacing.",
                            "Normal: +35% zombie health, +50% melee damage, +5% speed, denser waves.",
                            "Hard: +80% zombie health, +100% melee damage, +10% speed, relentless waves."
                        },
                        ZombiesDifficulty.values().length,
                        () -> this.difficulty = this.difficulty.next()
                    );
                }
            );

            this.rulesLayout.addRow(
                "Starting Gold", (s, x, y, w) -> {
                    this.startGoldField = this.addIntField(s, x, y, this.startGold, w, "Starting Gold", val -> "Each survivor starts with " + val + " gold to purchase weapons/doors.");
                }
            );

            this.rulesLayout.addRow(
                "Max Rounds", (s, x, y, w) -> {
                    this.maxRoundsField = this.addIntField(s, x, y, this.maxRounds, w, "Max Rounds", val -> "Survive up to round " + val + ". Bosses spawn on R10, R20, R30.");
                }
            );

            this.rulesLayout.addRow(
                "Intermission (s)", (s, x, y, w) -> {
                    this.intermissionField = this.addIntField(s, x, y, this.intermissionSeconds, w, "Intermission (s)", val -> "Seconds of peace between rounds to buy ammo, perks, and repair barricades.");
                }
            );

            this.rulesLayout.addRow(
                "Bleedout (s)", (s, x, y, w) -> {
                    this.bleedoutField = this.addIntField(s, x, y, this.bleedoutSeconds, w, "Bleedout (s)", val -> "Time in seconds before a downed survivor bleeds out and becomes a spectator.");
                }
            );

            this.rulesLayout.addRow(
                "Friendly Fire", (s, x, y, w) -> {
                    this.friendlyFireButton = this.addToggleButton(s, "Friendly Fire", () -> this.friendlyFire, x, y, w,
                        new BinaryTooltip("Guns and explosives can damage teammates.", "Bullets and rockets pass harmlessly through teammates."),
                        () -> this.friendlyFire = !this.friendlyFire);
                }
            );

            this.rulesLayout.addRow(
                "Endless Mode", (s, x, y, w) -> {
                    this.endlessModeButton = this.addToggleButton(s, "Endless Mode", () -> this.endlessMode, x, y, w,
                        new BinaryTooltip("Game continues infinitely past round 30 with escalating waves until defeat.", "Game ends in victory after round 30."),
                        () -> this.endlessMode = !this.endlessMode);
                }
            );
        } else if (this.moduleManager.isActive("tuning")) {
            initTuningWidgets(screen);
        }
    }

    private void initTuningWidgets(SessionScreen screen) {
        int moduleX = this.layout.mainPanel().x() + 14;
        int moduleY = this.layout.mainPanel().y() + 72;
        int moduleWidth = this.layout.mainPanel().width() - 28;
        int moduleHeight = this.layout.mainPanel().height() - 104;

        int tabY = moduleY + 12;

        // Subtab toggle: Weapons vs Difficulty
        ButtonWidget weaponsSubTabBtn = ButtonWidget.builder(
            Text.literal((this.activeTuningSubTab == TuningSubTab.WEAPONS ? "⚔ " : "") + "Weapons & Mystery Box"),
            btn -> {
                this.syncStateFromWidgets();
                this.activeTuningSubTab = TuningSubTab.WEAPONS;
                screen.rebuildWorkspaceChildren();
            }
        ).dimensions(moduleX + 12, tabY, 160, 20).build();
        screen.addWidget(weaponsSubTabBtn);

        ButtonWidget diffSubTabBtn = ButtonWidget.builder(
            Text.literal((this.activeTuningSubTab == TuningSubTab.DIFFICULTY ? "💀 " : "") + "Difficulty & Waves"),
            btn -> {
                this.syncStateFromWidgets();
                this.activeTuningSubTab = TuningSubTab.DIFFICULTY;
                screen.rebuildWorkspaceChildren();
            }
        ).dimensions(moduleX + 178, tabY, 150, 20).build();
        screen.addWidget(diffSubTabBtn);

        // Reset Button
        if (this.activeTuningSubTab == TuningSubTab.WEAPONS) {
            int resetBtnX = moduleX + moduleWidth - 145;
            ButtonWidget resetWeaponsBtn = ButtonWidget.builder(
                Text.literal("↺ Reset Weapons"),
                btn -> {
                    this.weaponConfig.resetToDefaults();
                    this.status = ValidationResult.info("Restored default weapon stats.");
                    screen.rebuildWorkspaceChildren();
                }
            ).dimensions(resetBtnX, tabY, 135, 20).build();
            screen.addWidget(resetWeaponsBtn);

            // Scroll buttons
            int maxRows = calculateMaxVisibleWeaponRows(moduleHeight);
            int totalWeapons = WeaponType.values().length;
            if (totalWeapons > maxRows) {
                ButtonWidget scrollUpBtn = ButtonWidget.builder(
                    Text.literal("▲"),
                    btn -> {
                        if (this.weaponScrollOffset > 0) {
                            this.syncStateFromWidgets();
                            this.weaponScrollOffset--;
                            screen.rebuildWorkspaceChildren();
                        }
                    }
                ).dimensions(resetBtnX - 52, tabY, 22, 20).build();
                scrollUpBtn.active = this.weaponScrollOffset > 0;
                screen.addWidget(scrollUpBtn);

                ButtonWidget scrollDownBtn = ButtonWidget.builder(
                    Text.literal("▼"),
                    btn -> {
                        if (this.weaponScrollOffset + maxRows < totalWeapons) {
                            this.syncStateFromWidgets();
                            this.weaponScrollOffset++;
                            screen.rebuildWorkspaceChildren();
                        }
                    }
                ).dimensions(resetBtnX - 26, tabY, 22, 20).build();
                scrollDownBtn.active = this.weaponScrollOffset + maxRows < totalWeapons;
                screen.addWidget(scrollDownBtn);
            }

            // Populate visible weapon rows
            int listY = moduleY + 38;
            int rowHeight = 25;
            int maxScroll = Math.max(0, totalWeapons - maxRows);
            this.weaponScrollOffset = Math.clamp(this.weaponScrollOffset, 0, maxScroll);

            int endIdx = Math.min(totalWeapons, this.weaponScrollOffset + maxRows);
            WeaponType[] allWeapons = WeaponType.values();

            for (int i = this.weaponScrollOffset; i < endIdx; i++) {
                WeaponType wt = allWeapons[i];
                int curY = listY + (i - this.weaponScrollOffset) * rowHeight;

                // Mystery Box Toggle Button
                int chestBtnX = moduleX + 185;
                ButtonWidget chestBtn;
                if (wt.getData().isMelee()) {
                    chestBtn = ButtonWidget.builder(Text.literal("Melee (N/A)"), b -> {}).dimensions(chestBtnX, curY + 2, 92, 18).build();
                    chestBtn.active = false;
                } else {
                    boolean inChest = this.weaponConfig.isAllowedInLuckyChest(wt);
                    chestBtn = ButtonWidget.builder(
                        Text.literal(inChest ? "✔ In Chest" : "✖ Excluded"),
                        b -> {
                            boolean newState = !this.weaponConfig.isAllowedInLuckyChest(wt);
                            this.weaponConfig.setInLuckyChest(wt, newState);
                            b.setMessage(Text.literal(newState ? "✔ In Chest" : "✖ Excluded"));
                        }
                    ).dimensions(chestBtnX, curY + 2, 92, 18).build();
                }
                screen.addWidget(chestBtn);

                // Damage text field
                int dmgFieldX = moduleX + 318;
                TextFieldWidget dmgField = new TextFieldWidget(this.client.textRenderer, dmgFieldX, curY + 2, 44, 18, Text.literal("Dmg"));
                dmgField.setMaxLength(6);
                dmgField.setText(String.format(Locale.ROOT, "%.1f", this.weaponConfig.getDamage(wt)));
                screen.addWidget(dmgField);

                // Reload text field
                int reloadFieldX = moduleX + 418;
                TextFieldWidget reloadField;
                if (wt.getData().isMelee()) {
                    reloadField = new TextFieldWidget(this.client.textRenderer, reloadFieldX, curY + 2, 46, 18, Text.literal("N/A"));
                    reloadField.setText("N/A");
                    reloadField.setEditable(false);
                    reloadField.active = false;
                } else {
                    reloadField = new TextFieldWidget(this.client.textRenderer, reloadFieldX, curY + 2, 46, 18, Text.literal("Sec"));
                    reloadField.setMaxLength(6);
                    reloadField.setText(String.format(Locale.ROOT, "%.2f", this.weaponConfig.getReloadTicks(wt) / 20.0f));
                }
                screen.addWidget(reloadField);

                this.activeWeaponRowWidgets.add(new WeaponRowWidgets(wt, chestBtn, dmgField, reloadField));
            }
        } else {
            // DIFFICULTY SUB-TAB
            int resetBtnX = moduleX + moduleWidth - 155;
            ButtonWidget resetDiffBtn = ButtonWidget.builder(
                Text.literal("↺ Reset Difficulty"),
                btn -> {
                    this.difficultyConfig.resetDifficulty(this.activeDifficultyTuning);
                    this.status = ValidationResult.info("Restored default " + this.activeDifficultyTuning.getDisplayName() + " difficulty.");
                    screen.rebuildWorkspaceChildren();
                }
            ).dimensions(resetBtnX, tabY, 145, 20).build();
            screen.addWidget(resetDiffBtn);

            // Difficulty select tabs
            int diffTabX = moduleX + 12;
            for (ZombiesDifficulty d : ZombiesDifficulty.values()) {
                boolean isCurrent = d == this.activeDifficultyTuning;
                ButtonWidget dBtn = ButtonWidget.builder(
                    Text.literal((isCurrent ? "▶ " : "") + d.getDisplayName()),
                    btn -> {
                        this.syncStateFromWidgets();
                        this.activeDifficultyTuning = d;
                        screen.rebuildWorkspaceChildren();
                    }
                ).dimensions(diffTabX, moduleY + 38, 90, 20).build();
                screen.addWidget(dBtn);
                diffTabX += 96;
            }

            // Attribute multipliers (Left Column) & Wave Spawning (Right Column)
            DifficultyEntry entry = this.difficultyConfig.getEntry(this.activeDifficultyTuning);
            int tableY = moduleY + 66;
            int colWidth = (moduleWidth - 36) / 2;
            int lx = moduleX + 12;
            int rx = moduleX + 24 + colWidth;

            // Left fields
            int leftFieldX = lx + colWidth - 65;
            this.diffHealthField = new TextFieldWidget(this.client.textRenderer, leftFieldX, tableY + 22, 55, 18, Text.literal("Health"));
            this.diffHealthField.setMaxLength(6);
            this.diffHealthField.setText(String.format(Locale.ROOT, "%.2f", entry.healthMultiplier()));
            screen.addWidget(this.diffHealthField);

            this.diffDamageField = new TextFieldWidget(this.client.textRenderer, leftFieldX, tableY + 46, 55, 18, Text.literal("Damage"));
            this.diffDamageField.setMaxLength(6);
            this.diffDamageField.setText(String.format(Locale.ROOT, "%.2f", entry.damageMultiplier()));
            screen.addWidget(this.diffDamageField);

            this.diffSpeedField = new TextFieldWidget(this.client.textRenderer, leftFieldX, tableY + 70, 55, 18, Text.literal("Speed"));
            this.diffSpeedField.setMaxLength(6);
            this.diffSpeedField.setText(String.format(Locale.ROOT, "%.2f", entry.speedMultiplier()));
            screen.addWidget(this.diffSpeedField);

            this.diffWinBreakField = new TextFieldWidget(this.client.textRenderer, leftFieldX, tableY + 94, 55, 18, Text.literal("Window"));
            this.diffWinBreakField.setMaxLength(6);
            this.diffWinBreakField.setText(String.format(Locale.ROOT, "%.2f", entry.windowBreakMultiplier()));
            screen.addWidget(this.diffWinBreakField);

            this.diffSpecialField = new TextFieldWidget(this.client.textRenderer, leftFieldX, tableY + 118, 55, 18, Text.literal("Special"));
            this.diffSpecialField.setMaxLength(6);
            this.diffSpecialField.setText(String.format(Locale.ROOT, "%.2f", entry.specialAttackMultiplier()));
            screen.addWidget(this.diffSpecialField);

            // Right fields
            int rightFieldX = rx + colWidth - 65;
            this.diffBaseMobsField = new IntFieldWidget(this.client.textRenderer, rightFieldX, tableY + 22, 55, 18, Text.literal("Base"));
            this.diffBaseMobsField.setMaxLength(5);
            this.diffBaseMobsField.setText(Integer.toString(entry.baseWaveMobs()));
            screen.addWidget(this.diffBaseMobsField);

            this.diffMobsPerRoundField = new IntFieldWidget(this.client.textRenderer, rightFieldX, tableY + 46, 55, 18, Text.literal("Scaling"));
            this.diffMobsPerRoundField.setMaxLength(5);
            this.diffMobsPerRoundField.setText(Integer.toString(entry.waveMobsPerRound()));
            screen.addWidget(this.diffMobsPerRoundField);

            this.diffMaxActiveField = new IntFieldWidget(this.client.textRenderer, rightFieldX, tableY + 70, 55, 18, Text.literal("Cap"));
            this.diffMaxActiveField.setMaxLength(5);
            this.diffMaxActiveField.setText(Integer.toString(entry.maxActiveMobs()));
            screen.addWidget(this.diffMaxActiveField);

            this.diffSpawnMinField = new IntFieldWidget(this.client.textRenderer, rightFieldX, tableY + 94, 55, 18, Text.literal("SpawnMin"));
            this.diffSpawnMinField.setMaxLength(5);
            this.diffSpawnMinField.setText(Integer.toString(entry.spawnCooldownMin()));
            screen.addWidget(this.diffSpawnMinField);

            this.diffSpawnRndField = new IntFieldWidget(this.client.textRenderer, rightFieldX, tableY + 118, 55, 18, Text.literal("SpawnRnd"));
            this.diffSpawnRndField.setMaxLength(5);
            this.diffSpawnRndField.setText(Integer.toString(entry.spawnCooldownRandom()));
            screen.addWidget(this.diffSpawnRndField);
        }
    }

    private int calculateMaxVisibleWeaponRows(int moduleHeight) {
        int available = moduleHeight - 48;
        return Math.max(4, available / 25);
    }

    @Override
    protected void renderGamemodeBackground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (this.moduleManager.isActive("rules")) {
            this.renderSettingsModulePanel(context, textRenderer, this.moduleManager.getActiveModule().label(), this.moduleManager.getActiveModule().accent());
        } else if (this.moduleManager.isActive("tuning")) {
            this.renderSettingsModulePanel(context, textRenderer, this.moduleManager.getActiveModule().label(), this.moduleManager.getActiveModule().accent());
            renderTuningBackground(context, textRenderer, mouseX, mouseY, delta);
        }
    }

    private void renderTuningBackground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        int moduleX = this.layout.mainPanel().x() + 14;
        int moduleY = this.layout.mainPanel().y() + 72;
        int moduleWidth = this.layout.mainPanel().width() - 28;
        int moduleHeight = this.layout.mainPanel().height() - 104;

        if (this.activeTuningSubTab == TuningSubTab.WEAPONS) {
            int listY = moduleY + 38;
            int rowHeight = 25;

            for (int idx = 0; idx < this.activeWeaponRowWidgets.size(); idx++) {
                var row = this.activeWeaponRowWidgets.get(idx);
                int curY = listY + idx * rowHeight;
                if (idx % 2 == 1) {
                    context.fill(moduleX + 10, curY, moduleX + moduleWidth - 10, curY + rowHeight, 0x14FFFFFF);
                }
                // Draw item icon
                context.drawItem(new ItemStack(row.type.getData().item()), moduleX + 14, curY + 2);
            }

            // Scrollbar track & thumb if scrollable
            int total = WeaponType.values().length;
            int maxRows = calculateMaxVisibleWeaponRows(moduleHeight);
            if (total > maxRows) {
                int trackX = moduleX + moduleWidth - 6;
                int trackY = listY;
                int trackH = maxRows * rowHeight;
                context.fill(trackX, trackY, trackX + 3, trackY + trackH, 0x33FFFFFF);

                float ratio = (float) maxRows / total;
                int thumbH = Math.max(12, (int) (trackH * ratio));
                float maxScroll = total - maxRows;
                int thumbY = trackY + (int) ((this.weaponScrollOffset / maxScroll) * (trackH - thumbH));
                context.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, UiTheme.ACCENT_GREEN);
            }
        } else {
            // Difficulty column cards
            int tableY = moduleY + 66;
            int colWidth = (moduleWidth - 36) / 2;
            int lx = moduleX + 12;
            int rx = moduleX + 24 + colWidth;

            UiRenderer.panel(context, lx, tableY, colWidth, 146, UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
            UiRenderer.panel(context, rx, tableY, colWidth, 146, UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        }
    }

    @Override
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (this.moduleManager.isActive("tuning")) {
            renderTuningForeground(context, textRenderer, mouseX, mouseY, delta);
        }
    }

    private void renderTuningForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        int moduleX = this.layout.mainPanel().x() + 14;
        int moduleY = this.layout.mainPanel().y() + 72;
        int moduleWidth = this.layout.mainPanel().width() - 28;

        if (this.activeTuningSubTab == TuningSubTab.WEAPONS) {
            int listY = moduleY + 38;
            int rowHeight = 25;

            for (int idx = 0; idx < this.activeWeaponRowWidgets.size(); idx++) {
                var row = this.activeWeaponRowWidgets.get(idx);
                int curY = listY + idx * rowHeight;

                // Weapon name & tier indicator
                String name = row.type.getData().displayName();
                int nameColor = row.type.isUpgraded() ? 0xFF55FFFF : UiTheme.TEXT;
                context.drawText(textRenderer, Text.literal(name), moduleX + 36, curY + 6, nameColor, false);

                // Dmg and Reload labels
                context.drawText(textRenderer, Text.literal("Dmg:"), moduleX + 288, curY + 6, UiTheme.TEXT_MUTED, false);
                if (!row.type.getData().isMelee()) {
                    context.drawText(textRenderer, Text.literal("Reload:"), moduleX + 375, curY + 6, UiTheme.TEXT_MUTED, false);
                }
            }
        } else {
            // Difficulty column text
            int tableY = moduleY + 66;
            int colWidth = (moduleWidth - 36) / 2;
            int lx = moduleX + 12;
            int rx = moduleX + 24 + colWidth;

            // Left Header & Labels
            context.drawText(textRenderer, Text.literal("Zombie Multipliers (" + this.activeDifficultyTuning.getDisplayName() + ")"), lx + 10, tableY + 8, UiTheme.ACCENT_BLUE, false);
            context.drawText(textRenderer, Text.literal("Health Multiplier:"), lx + 10, tableY + 26, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Damage Multiplier:"), lx + 10, tableY + 50, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Speed Multiplier:"), lx + 10, tableY + 74, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Barricade Break Mult:"), lx + 10, tableY + 98, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Special Attack Mult:"), lx + 10, tableY + 122, UiTheme.TEXT_MUTED, false);

            // Right Header & Labels
            context.drawText(textRenderer, Text.literal("Wave & Spawn Pacing (" + this.activeDifficultyTuning.getDisplayName() + ")"), rx + 10, tableY + 8, UiTheme.ACCENT_GREEN, false);
            context.drawText(textRenderer, Text.literal("Round 1 Base Mobs:"), rx + 10, tableY + 26, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Mobs Added / Round:"), rx + 10, tableY + 50, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Max Active Mob Cap:"), rx + 10, tableY + 74, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Spawn Min Ticks:"), rx + 10, tableY + 98, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Spawn Jitter Ticks:"), rx + 10, tableY + 122, UiTheme.TEXT_MUTED, false);
        }
    }

    @Override
    protected boolean gamemodeMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.moduleManager.isActive("tuning") && this.activeTuningSubTab == TuningSubTab.WEAPONS) {
            int moduleHeight = this.layout.mainPanel().height() - 104;
            int maxRows = calculateMaxVisibleWeaponRows(moduleHeight);
            int total = WeaponType.values().length;
            if (total > maxRows) {
                if (verticalAmount < 0 && this.weaponScrollOffset + maxRows < total) {
                    this.syncStateFromWidgets();
                    this.weaponScrollOffset = Math.min(total - maxRows, this.weaponScrollOffset + 1);
                    if (this.screenRef != null) {
                        this.screenRef.rebuildWorkspaceChildren();
                    }
                    return true;
                } else if (verticalAmount > 0 && this.weaponScrollOffset > 0) {
                    this.syncStateFromWidgets();
                    this.weaponScrollOffset = Math.max(0, this.weaponScrollOffset - 1);
                    if (this.screenRef != null) {
                        this.screenRef.rebuildWorkspaceChildren();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected List<Text> getSummaryLines() {
        return List.of(
            Text.literal("Difficulty: " + this.difficulty.getDisplayName()),
            Text.literal("Starting Gold: " + this.startGold),
            Text.literal("Max Rounds: " + this.maxRounds),
            Text.literal("Endless Mode: " + (this.endlessMode ? "ON" : "OFF")),
            Text.literal("Intermission: " + this.intermissionSeconds + "s"),
            Text.literal("Bleedout Timer: " + this.bleedoutSeconds + "s"),
            Text.literal("Friendly Fire: " + (this.friendlyFire ? "ON" : "OFF")),
            Text.literal("Weapon Tuning: " + (this.weaponConfig.getEntries().size()) + " weapons configured"),
            Text.literal("Difficulty Multipliers: Easy / Normal / Hard customized")
        );
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.syncStateFromWidgets();
        super.setActiveModule(moduleId);
    }

    @Override
    protected void syncStateFromWidgets() {
        if (this.moduleManager.isActive("rules")) {
            this.startGold = readClamped(this.startGoldField, this.startGold, 0, 100000);
            this.maxRounds = readClamped(this.maxRoundsField, this.maxRounds, 1, 100);
            this.intermissionSeconds = readClamped(this.intermissionField, this.intermissionSeconds, 0, 120);
            this.bleedoutSeconds = readClamped(this.bleedoutField, this.bleedoutSeconds, 5, 120);
        } else if (this.moduleManager.isActive("tuning")) {
            if (this.activeTuningSubTab == TuningSubTab.WEAPONS) {
                for (WeaponRowWidgets row : this.activeWeaponRowWidgets) {
                    if (row.damageField != null) {
                        float dmg = parsePositiveFloat(row.damageField, this.weaponConfig.getDamage(row.type));
                        this.weaponConfig.setDamage(row.type, dmg);
                    }
                    if (row.reloadField != null && !row.type.getData().isMelee()) {
                        float sec = parsePositiveFloat(row.reloadField, this.weaponConfig.getReloadTicks(row.type) / 20.0f);
                        int ticks = Math.max(1, Math.round(sec * 20.0f));
                        this.weaponConfig.setReloadTicks(row.type, ticks);
                    }
                }
            } else if (this.activeTuningSubTab == TuningSubTab.DIFFICULTY) {
                DifficultyEntry cur = this.difficultyConfig.getEntry(this.activeDifficultyTuning);

                float hp = parsePositiveFloat(this.diffHealthField, cur.healthMultiplier());
                float dmg = parsePositiveFloat(this.diffDamageField, cur.damageMultiplier());
                double spd = parsePositiveDouble(this.diffSpeedField, cur.speedMultiplier());
                float winBreak = parsePositiveFloat(this.diffWinBreakField, cur.windowBreakMultiplier());
                float spec = parsePositiveFloat(this.diffSpecialField, cur.specialAttackMultiplier());

                int baseMobs = readClamped(this.diffBaseMobsField, cur.baseWaveMobs(), 1, 200);
                int mobsPerRound = readClamped(this.diffMobsPerRoundField, cur.waveMobsPerRound(), 0, 50);
                int maxActive = readClamped(this.diffMaxActiveField, cur.maxActiveMobs(), 1, 64);
                int spawnMin = readClamped(this.diffSpawnMinField, cur.spawnCooldownMin(), 1, 200);
                int spawnRnd = readClamped(this.diffSpawnRndField, cur.spawnCooldownRandom(), 1, 200);

                this.difficultyConfig.setEntry(this.activeDifficultyTuning, new DifficultyEntry(
                    hp, dmg, spd, winBreak, baseMobs, mobsPerRound, maxActive, spawnMin, spawnRnd, spec
                ));
            }
        }
        LAST_ACTIVE_SETTINGS_CACHE.put(this.gameId(), this.exportPresetSettings());
    }

    private float parsePositiveFloat(TextFieldWidget field, float fallback) {
        if (field == null) return fallback;
        try {
            String clean = field.getText().replaceAll("[^0-9.]", "").trim();
            if (clean.isEmpty()) return fallback;
            float val = Float.parseFloat(clean);
            return val > 0.0f ? val : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private double parsePositiveDouble(TextFieldWidget field, double fallback) {
        if (field == null) return fallback;
        try {
            String clean = field.getText().replaceAll("[^0-9.]", "").trim();
            if (clean.isEmpty()) return fallback;
            double val = Double.parseDouble(clean);
            return val > 0.0 ? val : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    protected void applyPresetSettings(net.minecraft.nbt.NbtCompound settings) {
        if (settings == null) return;
        super.applyPresetSettings(settings);
        if (settings.contains("mapId", net.minecraft.nbt.NbtElement.STRING_TYPE)) {
            this.selectedMapId = settings.getString("mapId");
        }
        if (settings.contains("startGold", net.minecraft.nbt.NbtElement.NUMBER_TYPE)) {
            this.startGold = settings.getInt("startGold");
        }
        if (settings.contains("maxRounds", net.minecraft.nbt.NbtElement.NUMBER_TYPE)) {
            this.maxRounds = settings.getInt("maxRounds");
        }
        if (settings.contains("intermissionSeconds", net.minecraft.nbt.NbtElement.NUMBER_TYPE)) {
            this.intermissionSeconds = settings.getInt("intermissionSeconds");
        }
        if (settings.contains("bleedoutSeconds", net.minecraft.nbt.NbtElement.NUMBER_TYPE)) {
            this.bleedoutSeconds = settings.getInt("bleedoutSeconds");
        }
        if (settings.contains("friendlyFire", net.minecraft.nbt.NbtElement.NUMBER_TYPE)) {
            this.friendlyFire = settings.getBoolean("friendlyFire");
        }
        if (settings.contains("endlessMode", net.minecraft.nbt.NbtElement.NUMBER_TYPE)) {
            this.endlessMode = settings.getBoolean("endlessMode");
        }
        if (settings.contains("difficulty", net.minecraft.nbt.NbtElement.STRING_TYPE)) {
            try {
                this.difficulty = ZombiesDifficulty.valueOf(settings.getString("difficulty").toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {}
        }
        if (settings.contains("weaponConfig", net.minecraft.nbt.NbtElement.STRING_TYPE)) {
            WeaponCustomConfig parsed = WeaponCustomConfig.fromJsonString(settings.getString("weaponConfig"));
            if (parsed != null) {
                this.weaponConfig.copyFrom(parsed);
            }
        }
        if (settings.contains("difficultyConfig", net.minecraft.nbt.NbtElement.STRING_TYPE)) {
            ZombiesDifficultyConfig parsed = ZombiesDifficultyConfig.fromJsonString(settings.getString("difficultyConfig"));
            if (parsed != null) {
                this.difficultyConfig.copyFrom(parsed);
            }
        }

        if (this.startGoldField != null) this.startGoldField.setText(String.valueOf(this.startGold));
        if (this.maxRoundsField != null) this.maxRoundsField.setText(String.valueOf(this.maxRounds));
        if (this.intermissionField != null) this.intermissionField.setText(String.valueOf(this.intermissionSeconds));
        if (this.bleedoutField != null) this.bleedoutField.setText(String.valueOf(this.bleedoutSeconds));
        if (this.difficultyButton != null) this.difficultyButton.setMessage(Text.literal("Difficulty: " + this.difficulty.getDisplayName()));
    }

    @Override
    protected void resetToDefaultSettings() {
        this.startGold = 500;
        this.maxRounds = 30;
        this.intermissionSeconds = 10;
        this.bleedoutSeconds = 30;
        this.friendlyFire = false;
        this.endlessMode = false;
        this.difficulty = ZombiesDifficulty.EASY;
        this.weaponConfig.resetToDefaults();
        this.difficultyConfig.resetToDefaults();

        if (this.startGoldField != null) this.startGoldField.setText(String.valueOf(this.startGold));
        if (this.maxRoundsField != null) this.maxRoundsField.setText(String.valueOf(this.maxRounds));
        if (this.intermissionField != null) this.intermissionField.setText(String.valueOf(this.intermissionSeconds));
        if (this.bleedoutField != null) this.bleedoutField.setText(String.valueOf(this.bleedoutSeconds));
        if (this.difficultyButton != null) this.difficultyButton.setMessage(Text.literal("Difficulty: " + this.difficulty.getDisplayName()));
    }

    @Override
    public String title() {
        return "Zombies Setup";
    }

    @Override
    public String subtitle() {
        return "Co-op round-based undead survival";
    }

    @Override
    public String gameId() {
        return ZombiesDefinition.ID;
    }

    @Override
    protected ValidationResult validateGamemodeStart() {
        this.syncStateFromWidgets();
        if (this.playerGrid.getMembers("selected").isEmpty()) {
            return ValidationResult.error("Select at least one survivor.");
        }
        return ValidationResult.success("");
    }

    @Override
    protected void buildSessionSettings(SessionPayloadBuilder builder) {
        String mapId = this.selectedMapId == null || this.selectedMapId.isBlank() ? "dead_end" : this.selectedMapId;
        builder.settings().putString("mapId", mapId);
        builder.settings().putInt("startGold", this.startGold);
        builder.settings().putInt("maxRounds", this.maxRounds);
        builder.settings().putInt("intermissionSeconds", this.intermissionSeconds);
        builder.settings().putInt("bleedoutSeconds", this.bleedoutSeconds);
        builder.settings().putBoolean("friendlyFire", this.friendlyFire);
        builder.settings().putString("difficulty", this.difficulty.name());
        builder.settings().putBoolean("endlessMode", this.endlessMode);
        builder.settings().putString("weaponConfig", this.weaponConfig.toJsonString());
        builder.settings().putString("difficultyConfig", this.difficultyConfig.toJsonString());
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        builder.addGroup("players", "Survivors", this.playerGrid.getMembers("selected"));
    }
}
