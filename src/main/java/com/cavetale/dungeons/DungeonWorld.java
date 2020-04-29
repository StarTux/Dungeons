package com.cavetale.dungeons;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
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
    final String lootTable;
    Persistence persistence = new Persistence();

    @Data
    static class Persistence {
        List<Dungeon> dungeons = new ArrayList<>();
    }

    void loadPersistence() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) throw new IllegalStateException("World not loaded: " + worldName);
        File file = new File(world.getWorldFolder(), "dungeons.json");
        if (!file.isFile()) {
            persistence = new Persistence();
            return;
        }
        Gson gson = new Gson();
        try (FileReader fileReader = new FileReader(file)) {
            Persistence p = gson.fromJson(fileReader, Persistence.class);
            if (p != null) persistence = p;
        } catch (Exception e) {
            plugin.getLogger().warning("Error loading persistence from " + file);
            e.printStackTrace();
            return;
        }
    }

    void savePersistence() {
        World world = Bukkit.getWorld(worldName);
        final File file = new File(world.getWorldFolder(), "dungeons.json");
        final Gson gson = new Gson();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (FileWriter fileWriter = new FileWriter(file)) {
                    gson.toJson(persistence, fileWriter);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error saving persistence to " + file);
                    e.printStackTrace();
                    return;
                }
            });
    }

    Dungeon findDungeonAt(Block block) {
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

    Dungeon findNearestDungeon(Location location, boolean unraidedOnly) {
        int x = location.getBlockX();
        int z = location.getBlockZ();
        Dungeon nearestDungeon = null;
        int minDist = 0;
        for (Dungeon dungeon: persistence.getDungeons()) {
            if (unraidedOnly && dungeon.isRaided()) continue;
            int dist = Math.max(Math.abs(dungeon.lo.get(0) - x), Math.abs(dungeon.lo.get(2) - z));
            if (nearestDungeon == null || dist < minDist) {
                nearestDungeon = dungeon;
                minDist = dist;
            }
        }
        return nearestDungeon;
    }
}
