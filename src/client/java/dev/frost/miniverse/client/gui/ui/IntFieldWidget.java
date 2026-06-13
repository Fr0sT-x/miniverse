package dev.frost.miniverse.client.gui.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class IntFieldWidget extends TextFieldWidget {
    public IntFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text text) {
        super(textRenderer, x, y, width, height, text);
        this.setTextPredicate(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
    }

    public int getIntValue(int fallback) {
        try {
            return Integer.parseInt(this.getText().trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
