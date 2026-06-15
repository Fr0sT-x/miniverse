package dev.frost.miniverse.minigame.core.event;

import dev.frost.miniverse.minigame.core.SessionRoster;

public interface RosterAware {
    void onRosterChanged(SessionRoster roster);
}
