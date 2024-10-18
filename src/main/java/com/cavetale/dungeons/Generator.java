package com.cavetale.dungeons;

import com.cavetale.blockclip.BlockClip;
import com.cavetale.core.struct.Cuboid;
import com.cavetale.core.struct.Vec2i;
import com.cavetale.core.util.Json;
import com.cavetale.structure.cache.Structure;
import com.winthier.decorator.DecoratorEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.loot.LootTables;
import org.bukkit.loot.Lootable;
import static com.cavetale.dungeons.DungeonsPlugin.DUNGEON_KEY;
import static com.cavetale.dungeons.DungeonsPlugin.dungeonsPlugin;
import static com.cavetale.structure.StructurePlugin.structureCache;

@Getter @RequiredArgsConstructor
final class Generator implements Listener {
    private final String worldName;
    private final int margin;
    private ArrayList<DungeonClip> dungeons = new ArrayList<>();
    private int dungeonIndex = 0;
    private final Random random = new Random(System.nanoTime());

    @Value
    static class DungeonClip {
        String name;
        BlockClip clip;
    }

    protected int loadDungeons() {
        ArrayList<DungeonClip> ls = new ArrayList<>();
        HashSet<String> tags = new HashSet<>();
        File dir = new File(dungeonsPlugin().getDataFolder(), "dungeons");
        dir.mkdirs();
        for (File file: dir.listFiles()) {
            String name = file.getName();
            if (!name.endsWith(".json")) continue;
            name = name.substring(0, name.length() - 5);
            try {
                BlockClip clip = BlockClip.load(file);
                if (clip.getMetadata() != null && clip.getMetadata().containsKey("tags")) {
                    @SuppressWarnings("unchecked")
                    List<String> clipTags = (List<String>) clip.getMetadata().get("tags");
                    tags.addAll(clipTags);
                }
                ls.add(new DungeonClip(name, clip));
            } catch (Exception e) {
                dungeonsPlugin().getLogger().warning("Error loading " + file);
                e.printStackTrace();
            }
        }
        if (ls.isEmpty()) dungeonsPlugin().getLogger().warning("No dungeons loaded!");
        dungeons = ls;
        Collections.shuffle(dungeons, random);
        return ls.size();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDecorator(DecoratorEvent event) {
        if (event.getPass() != 2) return;
        if (dungeons.isEmpty()) return;
        Chunk chunk = event.getChunk();
        if (!chunk.getWorld().getName().equals(worldName)) return;
        trySpawnDungeon(chunk);
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

    /**
     * Try to spawn dungeon in place.
     * ox, oy, oz are world coordinates!
     *
     * @return true if dungeon was spawned, false otherwise
     */
    protected boolean trySpawnDungeon(Chunk chunk, int ox, int oy, int oz) {
        // Pick dungeon
        if (dungeonIndex >= dungeons.size()) dungeonIndex = 0;
        DungeonClip dungeonClip = dungeons.get(dungeonIndex);
        Cuboid boundingBox = new Cuboid(ox,
                                        oy,
                                        oz,
                                        ox + dungeonClip.clip.getSizeX() - 1,
                                        oy + dungeonClip.clip.getSizeY() - 1,
                                        oz + dungeonClip.clip.getSizeZ() - 1);
        Cuboid checkBox = new Cuboid(boundingBox.ax - margin,
                                     chunk.getWorld().getMinHeight(),
                                     boundingBox.az - margin,
                                     boundingBox.bx + margin,
                                     chunk.getWorld().getMaxHeight(),
                                     boundingBox.bz + margin);
        for (Structure nearby : structureCache().within(worldName, checkBox)) {
            if (nearby.getKey().equals(DUNGEON_KEY)) return false;
            if (nearby.getBoundingBox().overlaps(boundingBox)) return false;
        }
        Block origin = chunk.getWorld().getBlockAt(ox, oy, oz);
        // Size - 1
        final int ux = dungeonClip.clip.size().x - 1;
        final int uy = dungeonClip.clip.size().y - 1;
        final int uz = dungeonClip.clip.size().z - 1;
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
        dungeonIndex += 1; // no return
        pasteDungeon(dungeonClip.clip, origin, random);
        Dungeon dungeon = new Dungeon();
        dungeon.setName(dungeonClip.name);
        Structure structure = new Structure(worldName,
                                            DUNGEON_KEY,
                                            Vec2i.of(chunk.getX(), chunk.getZ()),
                                            boundingBox,
                                            Json.serialize(dungeon));
        structureCache().addStructure(structure);
        return true;
    }

    public static void pasteDungeon(BlockClip clip, Block origin, Random rnd) {
        int spawnerCount = 0;
        int chestCount = 0;
        clip.getStructure().place(origin.getLocation(),
                                  true,
                                  StructureRotation.NONE, Mirror.NONE,
                                  0, 1.0f, rnd);
        for (int z = 0; z < clip.getSizeZ(); z += 1) {
            for (int x = 0; x < clip.getSizeX(); x += 1) {
                origin.getRelative(x, clip.getSizeY(), z).setType(Material.BEDROCK, false);
            }
        }
        List<Chest> chests = new ArrayList<>();
        for (int y = 0; y <= clip.getSizeY(); y += 1) {
            for (int z = 0; z <= clip.getSizeZ(); z += 1) {
                for (int x = 0; x <= clip.getSizeX(); x += 1) {
                    Block block = origin.getRelative(x, y, z);
                    BlockState blockState = block.getState();
                    if (blockState instanceof CreatureSpawner spawner) {
                        spawner.setSpawnedType(SpawnedTypes.random(rnd));
                        spawner.update();
                        spawnerCount += 1;
                    } else if (blockState instanceof Container container) {
                        container.getInventory().clear();
                        if (container instanceof Lootable lootable) {
                            lootable.clearLootTable();
                            if (lootable instanceof Chest chest) {
                                chests.add(chest);
                            }
                        }
                        container.update();
                    }
                }
            }
        }
        if (!chests.isEmpty()) {
            final int chestIndex = rnd.nextInt(chests.size());
            for (int i = 0; i < chests.size(); i += 1) {
                Chest chest = chests.get(i);
                if (chestIndex == i) {
                    if (chest.getBlockData() instanceof org.bukkit.block.data.type.Chest blockData) {
                        blockData.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
                        chest.setBlockData(blockData);
                    }
                    chest.setLootTable(LootTables.SIMPLE_DUNGEON.getLootTable());
                    chest.update();
                } else {
                    final boolean applyPhysics = true;
                    chest.getBlock().setBlockData(Material.AIR.createBlockData(), applyPhysics);
                }
            }
        }
        dungeonsPlugin().getLogger()
            .info(origin.getWorld().getName() + ": Pasting dungeon "
                  + clip.getFilename() + " at"
                  + " " + (origin.getX() + clip.getSize().get(0) / 2)
                  + " " + (origin.getY() + clip.getSize().get(1) / 2)
                  + " " + (origin.getZ() + clip.getSize().get(2) / 2)
                  + " spawners=" + spawnerCount
                  + " chests=" + chests.size());
    }
}
