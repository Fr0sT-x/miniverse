package dev.frost.miniverse.client.gui.workspace;

public interface GamemodeWorkspaceView {
    String gameId();

    record WorkspaceModule(String id, String icon, String label, String group) {
    }

    interface ModuleProvider {
        java.util.List<WorkspaceModule> modules();

        String activeModuleId();

        void setActiveModule(String moduleId);
    }

    interface RosterRefreshable {
        void refreshRoster();
    }
}
