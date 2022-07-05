package com.cavetale.dungeons;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

@Getter @RequiredArgsConstructor
final class DungeonWorld {
    final DungeonsPlugin plugin;
    final String worldName;
    Persistence persistence = new Persistence();
    final Gson gson = new Gson();

    @Data
    static class Persistence {
        List<Dungeon> dungeons = new ArrayList<>();
    }

    public void loadPersistence() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) throw new IllegalStateException("World not loaded: " + worldName);
        File file = new File(world.getWorldFolder(), "dungeons.json");
        if (!file.isFile()) {
            persistence = new Persistence();
            return;
        }
        try (FileReader fileReader = new FileReader(file)) {
            Persistence p = gson.fromJson(fileReader, Persistence.class);
            if (p != null) persistence = p;
        } catch (Exception e) {
            plugin.getLogger().warning("Error loading persistence from " + file);
            e.printStackTrace();
            return;
        }
    }

    public void savePersistence() {
        World world = Bukkit.getWorld(worldName);
        File dir = world.getWorldFolder();
        final File file = new File(world.getWorldFolder(), "dungeons.json");
        final String json = gson.toJson(persistence);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    File tmpFile = File.createTempFile("tmp", ".json", dir);
                    try (PrintStream printStream = new PrintStream(tmpFile)) {
                        printStream.print(json);
                        tmpFile.renameTo(file);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error saving persistence to " + file, e);
                    return;
                }
            });
    }

    public Dungeon findDungeonAt(Block block) {
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        for (Dungeon dungeon: persistence.getDungeons()) {
            if (x >= dungeon.lo.get(0)
                && y >= dungeon.lo.get(1)
                && z >= dungeon.lo.get(2)
                && x <= dungeon.hi.get(0)
                && y <= dungeon.hi.get(1)
                && z <= dungeon.hi.get(2)) {
                return dungeon;
            }
        }
        return null;
    }

    public Dungeon findNearestDungeon(Location location, boolean unraidedOnly) {
        int x = location.getBlockX();
        int z = location.getBlockZ();
        Dungeon nearestDungeon = null;
        int minDist = 0;
        for (Dungeon dungeon: persistence.getDungeons()) {
            if (unraidedOnly && dungeon.isRaided()) continue;
            if (unraidedOnly && dungeon.isDiscovered()) continue;
            int dist = Math.max(Math.abs(dungeon.lo.get(0) - x), Math.abs(dungeon.lo.get(2) - z));
            if (nearestDungeon == null || dist < minDist) {
                nearestDungeon = dungeon;
                minDist = dist;
            }
        }
        return nearestDungeon;
    }
}
