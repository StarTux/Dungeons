package com.cavetale.dungeons;

import lombok.Data;

/**
 * The JSON structure stored in the Structure.
 */
@Data
public final class Dungeon {
    private String name;
    private boolean raided = false; // Chest opened?

    @Override
    public String toString() {
        return name + " raided:" + raided;
    }
}

