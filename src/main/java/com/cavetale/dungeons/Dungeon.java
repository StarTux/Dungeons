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
    public final List<Integer> lo, hi;
    private boolean raided = false;

    @Override
    public String toString() {
        return this.name
            + "(" + this.lo.get(0) + "," + this.lo.get(1) + "," + this.lo.get(2)
            + ")-(" + this.hi.get(0) + "," + this.hi.get(1) + "," + this.hi.get(2)
            + ") raided=" + this.raided;
    }
}

