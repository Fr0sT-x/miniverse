package dev.frost.miniverse.client.gui.workspace.framework;

import dev.frost.miniverse.client.gui.workspace.GamemodeWorkspaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public class WorkspaceModuleManager {
    private final List<RegisteredModule> registeredModules = new ArrayList<>();
    private String activeModuleId;

    public void register(String id, String icon, String label, String group, String description, int accent, BooleanSupplier isVisible) {
        this.registeredModules.add(new RegisteredModule(id, icon, label, group, description, accent, isVisible));
        if (this.activeModuleId == null) {
            this.activeModuleId = id;
        }
    }

    public void register(String id, String icon, String label, String group, String description, int accent) {
        this.register(id, icon, label, group, description, accent, () -> true);
    }

    public List<GamemodeWorkspaceView.WorkspaceModule> getVisibleModules() {
        List<GamemodeWorkspaceView.WorkspaceModule> result = new ArrayList<>();
        for (RegisteredModule module : this.registeredModules) {
            if (module.isVisible.getAsBoolean()) {
                result.add(new GamemodeWorkspaceView.WorkspaceModule(module.id, module.icon, module.label, module.group));
            }
        }
        return result;
    }

    public String getActiveModuleId() {
        if (this.activeModuleId != null) {
            Optional<RegisteredModule> active = this.getById(this.activeModuleId);
            if (active.isPresent() && !active.get().isVisible.getAsBoolean()) {
                this.activeModuleId = this.getFirstVisibleId();
            }
        }
        return this.activeModuleId;
    }

    public void setActiveModuleId(String moduleId) {
        if (this.getById(moduleId).isPresent()) {
            this.activeModuleId = moduleId;
        }
    }

    public boolean isActive(String moduleId) {
        return moduleId.equals(this.getActiveModuleId());
    }

    public RegisteredModule getActiveModule() {
        return this.getById(this.getActiveModuleId()).orElse(null);
    }

    private Optional<RegisteredModule> getById(String id) {
        if (id == null) return Optional.empty();
        return this.registeredModules.stream().filter(m -> m.id.equals(id)).findFirst();
    }

    private String getFirstVisibleId() {
        for (RegisteredModule m : this.registeredModules) {
            if (m.isVisible.getAsBoolean()) return m.id;
        }
        return null;
    }

    public record RegisteredModule(String id, String icon, String label, String group, String description, int accent, BooleanSupplier isVisible) {}
}
