package dev.frost.miniverse.client.transition;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class TransitionOverlayScreen extends Screen {
    public TransitionOverlayScreen() {
        super(Text.empty());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

