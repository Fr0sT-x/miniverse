package dev.frost.miniverse.client.gui.selector;

import dev.frost.miniverse.client.gui.selector.storage.Preset;
import dev.frost.miniverse.client.gui.selector.storage.SelectorDataManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PresetsManagerWidget<T> extends Screen {
    private final RegistrySelectorScreen<T> parent;
    private final String namespace;
    private TextFieldWidget presetNameField;

    public PresetsManagerWidget(RegistrySelectorScreen<T> parent, String namespace) {
        super(Text.literal("Presets Manager"));
        this.parent = parent;
        this.namespace = namespace;
    }

    @Override
    protected void init() {
        super.init();
        
        int panelWidth = 300;
        int panelHeight = 200;
        int x = (this.width - panelWidth) / 2;
        int y = (this.height - panelHeight) / 2;

        this.presetNameField = new TextFieldWidget(this.textRenderer, x + 10, y + 20, 200, 20, Text.literal("Preset Name"));
        this.addDrawableChild(this.presetNameField);

        ButtonWidget saveBtn = ButtonWidget.builder(Text.literal("Save Current"), b -> savePreset())
            .dimensions(x + 215, y + 20, 75, 20)
            .build();
        this.addDrawableChild(saveBtn);

        ButtonWidget closeBtn = ButtonWidget.builder(Text.literal("Close"), b -> this.client.setScreen(this.parent))
            .dimensions(x + panelWidth / 2 - 40, y + panelHeight - 30, 80, 20)
            .build();
        this.addDrawableChild(closeBtn);

        // List presets
        List<Preset> presets = SelectorDataManager.getPresetManager().getPresets(this.namespace);
        int listY = y + 50;
        for (Preset p : presets) {
            ButtonWidget loadBtn = ButtonWidget.builder(Text.literal("Load " + p.name()), b -> loadPreset(p))
                .dimensions(x + 10, listY, 200, 20)
                .build();
            this.addDrawableChild(loadBtn);

            ButtonWidget delBtn = ButtonWidget.builder(Text.literal("Delete"), b -> deletePreset(p))
                .dimensions(x + 215, listY, 75, 20)
                .build();
            this.addDrawableChild(delBtn);
            
            listY += 24;
            if (listY > y + panelHeight - 40) break; // simplistic layout
        }
    }

    private void savePreset() {
        String name = this.presetNameField.getText().trim();
        if (name.isEmpty()) return;

        Set<Identifier> ids = this.parent.getSelectedEntries().stream()
            .map(e -> this.parent.getProvider().getId(e))
            .collect(Collectors.toSet());
            
        SelectorDataManager.getPresetManager().addPreset(this.namespace, new Preset(name, ids));
        SelectorDataManager.save();
        this.init(this.client, this.width, this.height);
    }
    
    private void loadPreset(Preset preset) {
        this.parent.loadSelection(preset.entries());
        this.client.setScreen(this.parent);
    }
    
    private void deletePreset(Preset preset) {
        SelectorDataManager.getPresetManager().removePreset(this.namespace, preset.name());
        SelectorDataManager.save();
        this.init(this.client, this.width, this.height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }
}
