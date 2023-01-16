package com.cavetale.dungeons;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Data;

/**
 * The JSON structure stored in the Structure.
 */
@Data
public final class Dungeon {
    private String name;
    private boolean raided = false; // Chest opened?
    private boolean discovered = false; // Any block broken?
    private Set<UUID> discoveredBy = new HashSet<>();

    @Override
    public String toString() {
        return name
            + " raided:" + raided
            + " discovered:" + discovered;
    }
}

