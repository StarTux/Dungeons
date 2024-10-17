package com.cavetale.dungeons;

import java.util.Random;
import org.bukkit.entity.EntityType;

public final class SpawnedTypes {
    private static final EntityType[] SPAWNED_TYPES = new EntityType[] {
        EntityType.CAVE_SPIDER,
        EntityType.SPIDER,
        EntityType.ZOMBIE,
        EntityType.SKELETON,
        EntityType.CREEPER,
        // Eperimental
        EntityType.BLAZE,
        EntityType.BREEZE,
        EntityType.WITCH,
        EntityType.WITHER_SKELETON,
    };

    public static EntityType random(Random random) {
        return SPAWNED_TYPES[random.nextInt(SPAWNED_TYPES.length)];
    }

    private SpawnedTypes() { }
}
