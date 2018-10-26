package com.cavetale.dungeons;

import com.cavetale.blockclip.BlockClip;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;

@Getter @RequiredArgsConstructor
final class Generator implements Listener {
    final DungeonWorld dungeonWorld;
    final int margin;
    private ArrayList<Dungeon> dungeons = new ArrayList<>();
    private Map<String, Object> chestTag, spawnerTag;
    private final Random random = new Random(System.nanoTime());

    @Value
    static class Dungeon {
        String name;
        BlockClip clip;
    }

    int loadDungeons() {
        ArrayList<Dungeon> ls = new ArrayList<>();
        HashSet<String> tags = new HashSet<>();
        File dir = new File(this.dungeonWorld.plugin.getDataFolder(), "dungeons");
        dir.mkdirs();
        for (File file: dir.listFiles()) {
            String name = file.getName();
            if (!name.endsWith(".json")) continue;
            name = name.substring(0, name.length() - 5);
            try {
                BlockClip clip = BlockClip.load(file);
                if (clip.getMetadata().containsKey("tags")) {
                    tags.addAll((List<String>)clip.getMetadata().get("tags"));
                }
                ls.add(new Dungeon(name, clip));
            } catch (Exception e) {
                this.dungeonWorld.plugin.getLogger().warning("Error loading " + file);
                e.printStackTrace();
            }
        }
        System.out.println("Tags: " + tags);
        this.dungeons = ls;
        return ls.size();
    }

    void loadTags() {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(new File(new File(this.dungeonWorld.plugin.getDataFolder(), "data"), "chest_tag.json"))) {
            this.chestTag = gson.fromJson(reader, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
            this.chestTag = new HashMap<>();
        }
        try (FileReader reader = new FileReader(new File(new File(this.dungeonWorld.plugin.getDataFolder(), "data"), "spawner_tag.json"))) {
            this.spawnerTag = gson.fromJson(reader, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
            this.spawnerTag = new HashMap<>();
        }
        System.out.println(gson.toJson(chestTag));
        System.out.println(gson.toJson(spawnerTag));
    }

    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        Chunk chunk = event.getChunk();
        if (!chunk.getWorld().getName().equals(this.dungeonWorld.worldName)) return;
        Bukkit.getScheduler().runTask(this.dungeonWorld.plugin, () -> trySpawnDungeon(chunk));
    }

    boolean trySpawnDungeon(Chunk chunk) {
        int cx = chunk.getX();
        int cz = chunk.getZ();
        for (int i = 0; i < 10; i += 1) {
            // Random offset
            int dx = random.nextInt(16);
            int dz = random.nextInt(16);
            int dy = 8 + random.nextInt(40);
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

    boolean trySpawnDungeon(Chunk chunk, int ox, int oy, int oz) {
        // Check proximity
        for (DungeonWorld.PersistentDungeon pd: this.dungeonWorld.persistence.dungeons) {
            int ax = pd.lo.get(0) - margin;
            int bx = pd.hi.get(0) + margin;
            int az = pd.lo.get(2) - margin;
            int bz = pd.hi.get(2) + margin;
            if (ox >= ax && ox <= bx && oz >= az && oz <= bz) return false;
        }
        // Pick dungeon
        Block origin = chunk.getWorld().getBlockAt(ox, oy, oz);
        Dungeon dungeon = dungeons.get(random.nextInt(dungeons.size()));
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
                        return false;
                    default: break;
                    }
                    if (y == 0 || y == uy) {
                        if (block.isEmpty()) return false;
                    }
                }
            }
        }
        dungeon.clip.paste(origin, (block, vec, data, tag) -> {
                if (!block.isEmpty() && !block.getType().isSolid()) return true;
                if (data.getMaterial() == Material.SPAWNER) {
                    tag.putAll(this.spawnerTag);
                    return true;
                }
                if (data instanceof org.bukkit.block.data.type.Chest) {
                    tag.putAll(this.chestTag);
                    return true;
                }
                return true;
            });
        DungeonWorld.PersistentDungeon pd;
        pd = new DungeonWorld.PersistentDungeon(dungeon.name,
                                                Arrays.asList(ox, oy, oz),
                                                Arrays.asList(ox + ux, oy + uy, oz + uz));
        this.dungeonWorld.persistence.dungeons.add(pd);
        this.dungeonWorld.savePersistence();
        this.dungeonWorld.plugin.getLogger().info(chunk.getWorld().getName() + ": Dungeon " + dungeon.name + " pasted at " + (ox + ux / 2) + "," + (oy + uy / 2) + "," + (oz + uz / 2));
        return true;
    }
}
