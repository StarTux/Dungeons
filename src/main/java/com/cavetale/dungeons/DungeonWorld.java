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
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

@Getter @RequiredArgsConstructor
final class DungeonWorld {
    final DungeonsPlugin plugin;
    final String worldName;
    final String lootTable;
    Persistence persistence = new Persistence();

    @Value
    static class PersistentDungeon {
        public final String name;
        public final List<Integer> lo, hi;
    }

    @Data
    static class Persistence {
        List<PersistentDungeon> dungeons = new ArrayList<>();
    }

    void loadPersistence() {
        World world = Bukkit.getWorld(this.worldName);
        if (world == null) throw new IllegalStateException("World not loaded: " + this.worldName);
        File file = new File(world.getWorldFolder(), "dungeons.json");
        if (!file.isFile()) {
            this.persistence = new Persistence();
            return;
        }
        Gson gson = new Gson();
        try (FileReader fileReader = new FileReader(file)) {
            Persistence p = gson.fromJson(fileReader, Persistence.class);
            if (p != null) this.persistence = p;
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error loading persistence from " + file);
            e.printStackTrace();
            return;
        }
    }

    void savePersistence() {
        World world = Bukkit.getWorld(this.worldName);
        final File file = new File(world.getWorldFolder(), "dungeons.json");
        final Gson gson = new Gson();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (FileWriter fileWriter = new FileWriter(file)) {
                    gson.toJson(this.persistence, fileWriter);
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Error saving persistence to " + file + ": " + this.persistence);
                    return;
                }
            });
    }

    PersistentDungeon findNearestDungeon(Location location) {
        int x = location.getBlockX();
        int z = location.getBlockZ();
        PersistentDungeon nearestDungeon = null;
        int minDist = 0;
        for (PersistentDungeon dungeon: this.persistence.getDungeons()) {
            int dist = Math.max(Math.abs(dungeon.lo.get(0) - x), Math.abs(dungeon.lo.get(2) - z));
            if (nearestDungeon == null || dist < minDist) {
                nearestDungeon = dungeon;
                minDist = dist;
            }
        }
        return nearestDungeon;
    }
}
