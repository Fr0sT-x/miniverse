package dev.frost.miniverse.minigame.core;

public final class GameClock {
    private long ticks;

    public void reset() {
        this.ticks = 0L;
    }

    public long tick() {
        return ++this.ticks;
    }

    public void setTicks(long ticks) {
        this.ticks = Math.max(0L, ticks);
    }

    public long ticks() {
        return this.ticks;
    }

    public long seconds() {
        return this.ticks / 20L;
    }
}
