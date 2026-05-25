package dev.frost.miniverse.client.gui.ui;

import java.util.function.Function;

public final class UiAnimation {
    private UiAnimation() {
    }

    public static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public static float easeOutCubic(float value) {
        float t = clamp01(value) - 1.0F;
        return t * t * t + 1.0F;
    }

    public static float easeInOutQuad(float value) {
        float t = clamp01(value);
        return t < 0.5F ? 2.0F * t * t : -1.0F + (4.0F - 2.0F * t) * t;
    }

    public static float lerp(float from, float to, float progress) {
        return from + (to - from) * clamp01(progress);
    }

    public static int lerpColor(int from, int to, float progress) {
        float t = clamp01(progress);
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        int g = (int) (((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int alpha(int color, float alpha) {
        return ((int) (clamp01(alpha) * 255.0F) << 24) | (color & 0x00FFFFFF);
    }

    public static final class Value {
        private float current;
        private float start;
        private float target;
        private long startedAt;
        private int durationMs;
        private boolean animating;
        private Function<Float, Float> easing = UiAnimation::easeOutCubic;

        public Value(float initial) {
            this.current = initial;
            this.start = initial;
            this.target = initial;
        }

        public void animateTo(float target, int durationMs) {
            if (Math.abs(this.target - target) < 0.001F && this.animating) {
                return;
            }
            this.tick();
            this.start = this.current;
            this.target = target;
            this.durationMs = Math.max(1, durationMs);
            this.startedAt = System.currentTimeMillis();
            this.animating = true;
        }

        public void animateTo(float target, int durationMs, Function<Float, Float> easing) {
            this.easing = easing;
            this.animateTo(target, durationMs);
        }

        public void tick() {
            if (!this.animating) {
                return;
            }
            long elapsed = System.currentTimeMillis() - this.startedAt;
            if (elapsed >= this.durationMs) {
                this.current = this.target;
                this.animating = false;
                return;
            }
            this.current = lerp(this.start, this.target, this.easing.apply(elapsed / (float) this.durationMs));
        }

        public float get() {
            this.tick();
            return this.current;
        }
    }
}
