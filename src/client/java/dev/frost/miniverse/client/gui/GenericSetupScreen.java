package dev.frost.miniverse.client.gui;

import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class GenericSetupScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_PADDING = 16;
    private static final int ROW_HEIGHT = 30;
    private static final int FIELD_WIDTH = 190;
    private static final int BUTTON_HEIGHT = 20;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final MinigameEntry entry;
    private final List<FieldControl> controls = new ArrayList<>();
    private TextFieldWidget sessionNameField;
    private String statusMessage = "";

    public GenericSetupScreen(MinigameEntry entry) {
        super(Text.literal(entry.name() + " Setup"));
        this.entry = entry;
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();
        this.controls.clear();

        Layout layout = this.layout();
        this.sessionNameField = new TextFieldWidget(this.textRenderer, layout.fieldX(), layout.sessionY(), FIELD_WIDTH, BUTTON_HEIGHT, Text.literal("session name"));
        this.sessionNameField.setMaxLength(48);
        this.sessionNameField.setText(this.entry.id() + "-" + System.currentTimeMillis());
        this.addDrawableChild(this.sessionNameField);

        int y = layout.fieldsY();
        for (SessionSnapshotData.SetupField field : this.entry.fields()) {
            if (field.isBoolean()) {
                BooleanControl control = new BooleanControl(field, Boolean.parseBoolean(field.defaultValue()));
                control.button = this.addDrawableChild(ButtonWidget.builder(control.label(), button -> {
                    control.value = !control.value;
                    button.setMessage(control.label());
                }).dimensions(layout.fieldX(), y, FIELD_WIDTH, BUTTON_HEIGHT).build());
                this.controls.add(control);
            } else {
                TextFieldWidget widget = new TextFieldWidget(this.textRenderer, layout.fieldX(), y, FIELD_WIDTH, BUTTON_HEIGHT, Text.literal(field.label()));
                widget.setMaxLength(64);
                widget.setText(field.defaultValue());
                this.addDrawableChild(widget);
                this.controls.add(new TextControl(field, widget));
            }
            y += ROW_HEIGHT;
        }

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Create Session"), button -> this.createSession())
            .dimensions(layout.createX(), layout.footerY(), 116, BUTTON_HEIGHT)
            .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> this.client.setScreen(new SessionScreen()))
            .dimensions(layout.backX(), layout.footerY(), 68, BUTTON_HEIGHT)
            .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Layout layout = this.layout();
        context.fill(layout.panelX(), layout.panelY(), layout.panelX() + PANEL_WIDTH, layout.panelY() + layout.panelHeight(), 0xD0121212);
        context.fill(layout.panelX() + 1, layout.panelY() + 1, layout.panelX() + PANEL_WIDTH - 1, layout.panelY() + 34, 0xCC1F1F1F);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, layout.panelX() + PANEL_WIDTH / 2, layout.panelY() + 12, 0xFFFFFF);
        context.drawText(this.textRenderer, Text.literal("Session"), layout.labelX(), layout.sessionY() + 6, 0xFFE0E0E0, false);

        int y = layout.fieldsY();
        for (FieldControl control : this.controls) {
            context.drawText(this.textRenderer, Text.literal(control.field().label()), layout.labelX(), y + 6, 0xFFE0E0E0, false);
            y += ROW_HEIGHT;
        }

        if (!this.statusMessage.isEmpty()) {
            context.drawText(this.textRenderer, Text.literal(this.statusMessage), layout.labelX(), layout.footerY() - 14, 0xFFFFA0A0, false);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void createSession() {
        NbtCompound settings = new NbtCompound();
        settings.putString("seedMode", "random");
        for (FieldControl control : this.controls) {
            if (!control.write(settings)) {
                this.statusMessage = "Invalid value for " + control.field().label();
                return;
            }
        }

        NbtCompound plan = new NbtCompound();
        plan.putString("game", this.entry.id());
        plan.putString("name", this.sessionNameField.getText().trim());
        plan.putBoolean("launch", false);
        plan.put("settings", settings);
        plan.put("groups", new NbtList());

        ClientPlayNetworking.send(new NetworkConstants.CreateSessionPayload(this.entry.id(), this.sessionNameField.getText().trim(), plan));
        ClientPlayNetworking.send(new NetworkConstants.RequestSessionsPayload("refresh"));
        this.client.setScreen(new SessionScreen());
    }

    private Layout layout() {
        int fieldRows = Math.max(1, this.entry.fields().size());
        int panelHeight = 88 + fieldRows * ROW_HEIGHT + 44;
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = Math.max(18, (this.height - panelHeight) / 2);
        int labelX = panelX + PANEL_PADDING;
        int fieldX = panelX + PANEL_WIDTH - PANEL_PADDING - FIELD_WIDTH;
        int sessionY = panelY + 50;
        int fieldsY = sessionY + ROW_HEIGHT;
        int footerY = panelY + panelHeight - PANEL_PADDING - BUTTON_HEIGHT;
        return new Layout(panelX, panelY, panelHeight, labelX, fieldX, sessionY, fieldsY, footerY, fieldX + FIELD_WIDTH - 116, labelX);
    }

    private interface FieldControl {
        SessionSnapshotData.SetupField field();

        boolean write(NbtCompound settings);
    }

    private record TextControl(SessionSnapshotData.SetupField field, TextFieldWidget widget) implements FieldControl {
        @Override
        public boolean write(NbtCompound settings) {
            String value = this.widget.getText().trim();
            if (this.field.required() && value.isBlank()) {
                return false;
            }
            if (this.field.isInteger()) {
                try {
                    int parsed = Integer.parseInt(value);
                    if (this.field.min() != 0 || this.field.max() != 0) {
                        if (parsed < this.field.min() || parsed > this.field.max()) {
                            return false;
                        }
                    }
                    settings.putInt(this.field.key(), parsed);
                    return true;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
            settings.putString(this.field.key(), value);
            return true;
        }
    }

    private static final class BooleanControl implements FieldControl {
        private final SessionSnapshotData.SetupField field;
        private boolean value;
        private ButtonWidget button;

        private BooleanControl(SessionSnapshotData.SetupField field, boolean value) {
            this.field = field;
            this.value = value;
        }

        @Override
        public SessionSnapshotData.SetupField field() {
            return this.field;
        }

        @Override
        public boolean write(NbtCompound settings) {
            settings.putBoolean(this.field.key(), this.value);
            return true;
        }

        private Text label() {
            return Text.literal(this.value ? "Enabled" : "Disabled");
        }
    }

    private record Layout(int panelX, int panelY, int panelHeight, int labelX, int fieldX, int sessionY, int fieldsY, int footerY, int createX, int backX) {
    }
}
