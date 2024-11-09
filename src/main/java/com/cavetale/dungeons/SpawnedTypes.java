package com.cavetale.dungeons;

import java.util.Random;
import org.bukkit.entity.EntityType;

public final class SpawnedTypes {
    private static final EntityType[] DUNGEON_SPAWNER = new EntityType[] {
        EntityType.CAVE_SPIDER,
        EntityType.SPIDER,
        EntityType.ZOMBIE,
        EntityType.SKELETON,
        EntityType.CREEPER,
        EntityType.BLAZE,
        EntityType.WITHER_SKELETON,
        EntityType.HUSK,
        EntityType.DROWNED,
    };

    private static final EntityType[] TRIAL_SPAWNER = new EntityType[] {
        EntityType.CAVE_SPIDER,
        EntityType.SPIDER,
        EntityType.ZOMBIE,
        EntityType.SKELETON,
        EntityType.CREEPER,
        // Eperimental
        EntityType.BLAZE,
        EntityType.BREEZE,
        EntityType.WITHER_SKELETON,
    };

    public static EntityType trialSpawner(Random random) {
        return TRIAL_SPAWNER[random.nextInt(TRIAL_SPAWNER.length)];
    }

    public static EntityType dungeonSpawner(Random random) {
        return DUNGEON_SPAWNER[random.nextInt(DUNGEON_SPAWNER.length)];
    }

    private SpawnedTypes() { }
}
