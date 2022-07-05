package com.cavetale.dungeons;

import java.util.List;
import lombok.Data;

/**
 * Persistent dungeon data used by Generator and Manager, stored in
 * DungeonLootEvent.
 */
@Data
public final class Dungeon {
    public final String name;
    public final List<Integer> lo;
    public final List<Integer> hi;
    private boolean raided = false; // Chest opened?
    private boolean discovered = false; // Any block broken?

    @Override
    public String toString() {
        return name
            + "(" + lo.get(0) + "," + lo.get(1) + "," + lo.get(2)
            + ")-(" + hi.get(0) + "," + hi.get(1) + "," + hi.get(2)
            + ") raided=" + raided
            + ") discovered=" + discovered;
    }
}

