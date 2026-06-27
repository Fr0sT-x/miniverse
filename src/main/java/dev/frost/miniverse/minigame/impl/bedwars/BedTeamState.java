package dev.frost.miniverse.minigame.impl.bedwars;

public class BedTeamState {
    private boolean bedAlive = true;

    public boolean isBedAlive() {
        return bedAlive;
    }

    public void destroyBed() {
        this.bedAlive = false;
    }
}
