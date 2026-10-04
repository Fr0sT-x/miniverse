package dev.frost.miniverse.client.gui.ui;

import dev.frost.miniverse.client.gui.workspace.framework.WorkspaceTooltip;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class IntFieldWidget extends TextFieldWidget {
    private String fieldName = "Field";
    private int minValue = Integer.MIN_VALUE;
    private int maxValue = Integer.MAX_VALUE;
    private boolean isValid = true;
    private String validationError = null;
    private Consumer<Integer> valueChangeListener = null;
    private WorkspaceTooltip workspaceTooltip = null;

    public IntFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text text) {
        super(textRenderer, x, y, width, height, text);
        this.setTextPredicate(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
        this.setChangedListener(this::onTextChanged);
    }

    public IntFieldWidget(TextRenderer textRenderer, String fieldName, int x, int y, int width, int height,
                          int initialValue, int minValue, int maxValue,
                          WorkspaceTooltip tooltip, Consumer<Integer> valueChangeListener) {
        super(textRenderer, x, y, width, height, Text.literal(fieldName));
        this.fieldName = (fieldName != null && !fieldName.isBlank()) ? fieldName : "Field";
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.workspaceTooltip = tooltip;
        this.valueChangeListener = valueChangeListener;
        this.setTextPredicate(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
        this.setChangedListener(this::onTextChanged);
        this.setText(Integer.toString(initialValue));
        this.validate();
    }

    public void configure(String fieldName, int minValue, int maxValue, WorkspaceTooltip tooltip, Consumer<Integer> valueChangeListener) {
        this.fieldName = (fieldName != null && !fieldName.isBlank()) ? fieldName : this.fieldName;
        this.minValue = minValue;
        this.maxValue = maxValue;
        if (tooltip != null) {
            this.workspaceTooltip = tooltip;
        }
        if (valueChangeListener != null) {
            this.valueChangeListener = valueChangeListener;
        }
        this.validate();
    }

    private void onTextChanged(String text) {
        String trimmed = text != null ? text.trim() : "";
        if (trimmed.isEmpty()) {
            this.isValid = false;
            this.validationError = "Input field of '" + this.fieldName + "' is empty.";
            return;
        }

        if (trimmed.equals("-")) {
            this.isValid = false;
            this.validationError = "Input field of '" + this.fieldName + "' is not a valid number.";
            return;
        }

        try {
            int val = Integer.parseInt(trimmed);
            if (val < this.minValue || val > this.maxValue) {
                this.isValid = false;
                this.validationError = "Input field of '" + this.fieldName + "' must be between " + this.minValue + " and " + this.maxValue + ".";
            } else {
                this.isValid = true;
                this.validationError = null;
                if (this.valueChangeListener != null) {
                    this.valueChangeListener.accept(val);
                }
            }
        } catch (NumberFormatException e) {
            this.isValid = false;
            this.validationError = "Input field of '" + this.fieldName + "' is not a valid number.";
        }
    }

    public void validate() {
        this.onTextChanged(this.getText());
    }

    public boolean isValid() {
        return this.isValid;
    }

    public String getValidationError() {
        return this.validationError;
    }

    public String getFieldName() {
        return this.fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
        this.validate();
    }

    public int getMinValue() {
        return this.minValue;
    }

    public int getMaxValue() {
        return this.maxValue;
    }

    public void setBounds(int min, int max) {
        this.minValue = min;
        this.maxValue = max;
        this.validate();
    }

    public void setValueChangeListener(Consumer<Integer> valueChangeListener) {
        this.valueChangeListener = valueChangeListener;
    }

    public WorkspaceTooltip getWorkspaceTooltip() {
        return this.workspaceTooltip;
    }

    public void setWorkspaceTooltip(WorkspaceTooltip tooltip) {
        this.workspaceTooltip = tooltip;
    }

    public int getIntValue(int fallback) {
        if (!this.isValid) return fallback;
        try {
            return Integer.parseInt(this.getText().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);
        if (!this.isValid) {
            // Prominent red outline highlighting the invalid state
            UiRenderer.border(context, this.getX() - 1, this.getY() - 1, this.getWidth() + 2, this.getHeight() + 2, UiTheme.ACCENT_RED);
        }
    }
}
