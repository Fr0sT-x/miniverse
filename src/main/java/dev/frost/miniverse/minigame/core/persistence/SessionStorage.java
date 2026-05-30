package dev.frost.miniverse.minigame.core.persistence;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface SessionStorage {
    void save(SessionData data) throws IOException;

    Optional<SessionData> load(String sessionId) throws IOException;

    List<SessionData> loadAll() throws IOException;

    void delete(String sessionId) throws IOException;
}
