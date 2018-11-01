package com.cavetale.dungeons;

import java.util.ArrayList;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class DungeonsPlugin extends JavaPlugin {
    private final ArrayList<Generator> generators = new ArrayList<>();
    private final ArrayList<Manager> managers = new ArrayList<>();

    @Override
    public void onEnable() {
        reloadConfig();
        saveDefaultConfig();
        saveResource("data/chest_tag.json", false);
        saveResource("data/spawner_tag.json", false);
        int margin = getConfig().getInt("margin");
        String lootTable = getConfig().getString("loot_table");
        for (String worldName: getConfig().getStringList("worlds")) {
            DungeonWorld dungeonWorld = new DungeonWorld(this, worldName, lootTable);
            dungeonWorld.loadPersistence();
            Manager manager = new Manager(dungeonWorld);
            getServer().getPluginManager().registerEvents(manager, this);
            managers.add(manager);
            getLogger().info("Manager enabled for world \"" + worldName + "\" lootTable=" + lootTable);
            if (getConfig().getBoolean("generate")) {
                Generator generator = new Generator(dungeonWorld, margin);
                int dc = generator.loadDungeons();
                generator.loadTags();
                getServer().getPluginManager().registerEvents(generator, this);
                generators.add(generator);
                getLogger().info("Generator enabled for world \"" + worldName + "\", dungeons=" + dc + " margin=" + margin);
            }
        }
    }

    @Override
    public void onDisable() {
        generators.clear();
        managers.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        Player player = sender instanceof Player ? (Player)sender : null;
        if (args.length == 0) return false;
        switch (args[0]) {
        case "locate": {
            int x = player.getLocation().getBlockX();
            int z = player.getLocation().getBlockZ();
            for (Manager manager: managers) {
                if (!manager.dungeonWorld.getWorldName().equals(player.getWorld().getName())) continue;
                DungeonWorld.PersistentDungeon nearestDungeon = manager.dungeonWorld.findNearestDungeon(player.getLocation());
                if (nearestDungeon != null) {
                    player.sendMessage("Nearest dungeon at "
                                       + ((nearestDungeon.lo.get(0) + nearestDungeon.hi.get(0)) / 2) + ","
                                       + ((nearestDungeon.lo.get(1) + nearestDungeon.hi.get(1)) / 2) + ","
                                       + ((nearestDungeon.lo.get(2) + nearestDungeon.hi.get(2)) / 2)
                                       + " named " + nearestDungeon.name);
                } else {
                    player.sendMessage("No dungeon found");
                }
                return true;
            }
            player.sendMessage("This world does not spawn dungeons.");
            return true;
        }
        default: return false;
        }
    }
}
