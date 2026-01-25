package org.fhirframework.core.config;

import org.fhirframework.core.interaction.InteractionType;

import java.util.EnumSet;
import java.util.Set;

/**
 * Configuration for enabled FHIR interactions on a resource.
 */
public class InteractionsConfig {

    private boolean read = true;
    private boolean vread = true;
    private boolean create = true;
    private boolean update = true;
    private boolean patch = true;
    private boolean delete = false;
    private boolean search = true;
    private boolean history = true;

    public InteractionsConfig() {
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public boolean isVread() {
        return vread;
    }

    public void setVread(boolean vread) {
        this.vread = vread;
    }

    public boolean isCreate() {
        return create;
    }

    public void setCreate(boolean create) {
        this.create = create;
    }

    public boolean isUpdate() {
        return update;
    }

    public void setUpdate(boolean update) {
        this.update = update;
    }

    public boolean isPatch() {
        return patch;
    }

    public void setPatch(boolean patch) {
        this.patch = patch;
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    public boolean isSearch() {
        return search;
    }

    public void setSearch(boolean search) {
        this.search = search;
    }

    public boolean isHistory() {
        return history;
    }

    public void setHistory(boolean history) {
        this.history = history;
    }

    /**
     * Checks if the given interaction type is enabled.
     */
    public boolean isEnabled(InteractionType type) {
        return switch (type) {
            case READ -> read;
            case VREAD -> vread;
            case CREATE -> create;
            case UPDATE -> update;
            case PATCH -> patch;
            case DELETE -> delete;
            case SEARCH -> search;
            case HISTORY -> history;
        };
    }

    /**
     * Returns the set of enabled interaction types.
     */
    public Set<InteractionType> getEnabledInteractions() {
        Set<InteractionType> enabled = EnumSet.noneOf(InteractionType.class);
        if (read) enabled.add(InteractionType.READ);
        if (vread) enabled.add(InteractionType.VREAD);
        if (create) enabled.add(InteractionType.CREATE);
        if (update) enabled.add(InteractionType.UPDATE);
        if (patch) enabled.add(InteractionType.PATCH);
        if (delete) enabled.add(InteractionType.DELETE);
        if (search) enabled.add(InteractionType.SEARCH);
        if (history) enabled.add(InteractionType.HISTORY);
        return enabled;
    }

    @Override
    public String toString() {
        return "InteractionsConfig{read=" + read + ", vread=" + vread +
                ", create=" + create + ", update=" + update +
                ", patch=" + patch + ", delete=" + delete +
                ", search=" + search + ", history=" + history + "}";
    }
}
