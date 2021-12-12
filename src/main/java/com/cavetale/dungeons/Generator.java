package com.cavetale.dungeons;

import com.cavetale.blockclip.BlockClip;
import com.winthier.decorator.DecoratorEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.loot.LootTables;

@Getter @RequiredArgsConstructor
final class Generator implements Listener {
    final DungeonWorld dungeonWorld;
    final int margin;
    private ArrayList<DungeonClip> dungeons = new ArrayList<>();
    private int dungeonIndex = 0;
    private final Random random = new Random(System.nanoTime());
    private final EntityType[] spawnedTypes = new EntityType[] {
        EntityType.CAVE_SPIDER,
        EntityType.SPIDER,
        EntityType.ZOMBIE,
        EntityType.SKELETON,
        EntityType.CREEPER,
    };

    @Value
    static class DungeonClip {
        String name;
        BlockClip clip;
    }

    protected int loadDungeons() {
        ArrayList<DungeonClip> ls = new ArrayList<>();
        HashSet<String> tags = new HashSet<>();
        File dir = new File(dungeonWorld.plugin.getDataFolder(), "dungeons");
        dir.mkdirs();
        for (File file: dir.listFiles()) {
            String name = file.getName();
            if (!name.endsWith(".json")) continue;
            name = name.substring(0, name.length() - 5);
            try {
                BlockClip clip = BlockClip.load(file);
                if (clip.getMetadata().containsKey("tags")) {
                    @SuppressWarnings("unchecked")
                    List<String> clipTags = (List<String>) clip.getMetadata().get("tags");
                    tags.addAll(clipTags);
                }
                ls.add(new DungeonClip(name, clip));
            } catch (Exception e) {
                dungeonWorld.plugin.getLogger().warning("Error loading " + file);
                e.printStackTrace();
            }
        }
        if (ls.isEmpty()) dungeonWorld.plugin.getLogger().warning("No dungeons loaded!");
        dungeons = ls;
        Collections.shuffle(dungeons, random);
        return ls.size();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDecorator(DecoratorEvent event) {
        if (dungeons.isEmpty()) return;
        Chunk chunk = event.getChunk();
        if (!chunk.getWorld().getName().equals(dungeonWorld.worldName)) return;
        Bukkit.getScheduler().runTask(dungeonWorld.plugin, () -> trySpawnDungeon(chunk));
    }

    protected boolean trySpawnDungeon(Chunk chunk) {
        int cx = chunk.getX();
        int cz = chunk.getZ();
        final int minY = 8 + chunk.getWorld().getMinHeight();
        final int maxY = 48;
        for (int i = 0; i < 10; i += 1) {
            // Random offset
            int dx = random.nextInt(16);
            int dz = random.nextInt(16);
            int dy = minY + random.nextInt(maxY - minY);
            // Origin
            int ox = cx * 16 + dx;
            int oy = dy;
            int oz = cz * 16 + dz;
            if (trySpawnDungeon(chunk, ox, oy, oz)) {
                return true;
            }
        }
        return false;
    }

    protected boolean trySpawnDungeon(Chunk chunk, int ox, int oy, int oz) {
        // Check proximity
        for (Dungeon pd: dungeonWorld.persistence.dungeons) {
            int ax = pd.lo.get(0) - margin;
            int bx = pd.hi.get(0) + margin;
            int az = pd.lo.get(2) - margin;
            int bz = pd.hi.get(2) + margin;
            if (ox >= ax && ox <= bx && oz >= az && oz <= bz) return false;
        }
        // Pick dungeon
        Block origin = chunk.getWorld().getBlockAt(ox, oy, oz);
        if (dungeonIndex >= dungeons.size()) dungeonIndex = 0;
        DungeonClip dungeon = dungeons.get(dungeonIndex);
        // Size - 1
        final int ux = dungeon.clip.size().x - 1;
        final int uy = dungeon.clip.size().y - 1;
        final int uz = dungeon.clip.size().z - 1;
        // Check out location
        for (int y = 0; y <= uy; y += 1) {
            for (int z = 0; z <= uz; z += 1) {
                for (int x = 0; x <= ux; x += 1) {
                    Block block = origin.getRelative(x, y, z);
                    switch (block.getType()) {
                    case SPAWNER:
                    case MOSSY_COBBLESTONE:
                    case CHEST:
                    case TRAPPED_CHEST:
                    case RAIL:
                    case LAVA:
                    case WATER:
                    case OBSIDIAN:
                    case MOSS_BLOCK:
                        return false;
                    default: break;
                    }
                    if (y == 0 || y == uy) {
                        if (block.isEmpty()) return false;
                    }
                }
            }
        }
        dungeonIndex += 1;
        int spawnerCount = 0;
        int chestCount = 0;
        for (BlockState blockState : dungeon.clip.getBlockStates()) {
            if (blockState instanceof CreatureSpawner spawner) {
                spawner.setSpawnedType(spawnedTypes[random.nextInt(spawnedTypes.length)]);
                spawnerCount += 1;
            } else if (blockState instanceof Chest chest) {
                chest.setLootTable(LootTables.SIMPLE_DUNGEON.getLootTable());
                chestCount += 1;
            }
        }
        dungeonWorld.plugin.getLogger()
            .info(chunk.getWorld().getName() + ": Pasting dungeon "
                  + dungeon.name + " at"
                  + " " + dungeonWorld.worldName
                  + " " + (ox + ux / 2)
                  + " " + (oy + uy / 2)
                  + " " + (oz + uz / 2)
                  + " spawners=" + spawnerCount
                  + " chests=" + chestCount);
        dungeon.clip.paste(origin);
        Dungeon pd;
        pd = new Dungeon(dungeon.name,
                         Arrays.asList(ox, oy, oz),
                         Arrays.asList(ox + ux, oy + uy, oz + uz));
        dungeonWorld.persistence.dungeons.add(pd);
        dungeonWorld.savePersistence();
        return true;
    }
}
