package com.cavetale.dungeons;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class DungeonsPlugin extends JavaPlugin {
    private final ArrayList<Generator> generators = new ArrayList<>();
    private final ArrayList<Manager> managers = new ArrayList<>();
    ItemStack specialItem;
    double specialChance;
    File specialItemFile;

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
            if (getConfig().getBoolean("manage")) {
                specialItemFile = new File(getDataFolder(), "specialItem.yml");
                loadSpecialItem();
                Manager manager = new Manager(dungeonWorld);
                getServer().getPluginManager().registerEvents(manager, this);
                managers.add(manager);
                getLogger().info("Manager enabled for world \""
                                 + worldName + "\" lootTable=" + lootTable);
            }
            if (getConfig().getBoolean("generate")) {
                Generator generator = new Generator(dungeonWorld, margin);
                int dc = generator.loadDungeons();
                generator.loadTags();
                getServer().getPluginManager().registerEvents(generator, this);
                generators.add(generator);
                getLogger().info("Generator enabled for world \""
                                 + worldName + "\", dungeons=" + dc
                                 + " margin=" + margin);
            }
        }
    }

    @Override
    public void onDisable() {
        generators.clear();
        managers.clear();
    }

    Manager managerOf(World world) {
        for (Manager manager: managers) {
            if (manager.dungeonWorld.getWorldName().equals(world.getName())) return manager;
        }
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;
        if (args.length == 0) return false;
        switch (args[0]) {
        case "locate": {
            if (player == null) {
                sender.sendMessage("Player expected");
                return true;
            }
            int x = player.getLocation().getBlockX();
            int z = player.getLocation().getBlockZ();
            Manager manager = managerOf(player.getWorld());
            if (manager == null) {
                player.sendMessage("This world does not spawn dungeons.");
                return true;
            }
            Dungeon nearest = manager.dungeonWorld.findNearestDungeon(player.getLocation(), false);
            if (nearest == null) {
                player.sendMessage("No dungeon found");
            } else {
                int dx = (nearest.lo.get(0) + nearest.hi.get(0)) / 2;
                int dy = (nearest.lo.get(1) + nearest.hi.get(1)) / 2;
                int dz = (nearest.lo.get(2) + nearest.hi.get(2)) / 2;
                String cmd = "/tp " + dx + " " + dy + " " + dz;
                player.sendMessage(Component.text("Nearest dungeon: " + nearest.toString(), NamedTextColor.YELLOW)
                                   .hoverEvent(HoverEvent.showText(Component.text(cmd, NamedTextColor.YELLOW)))
                                   .clickEvent(ClickEvent.suggestCommand(cmd)));
            }
            return true;
        }
        case "info": {
            if (player == null) {
                sender.sendMessage("Player expected");
                return true;
            }
            Manager manager = managerOf(player.getWorld());
            if (manager == null) {
                player.sendMessage("This world does not spawn dungeons.");
                return true;
            }
            Dungeon dungeon = manager.dungeonWorld.findDungeonAt(player.getLocation().getBlock());
            if (dungeon == null) {
                player.sendMessage("There is no dungeon here");
            } else {
                player.sendMessage("Current dungeon: " + dungeon.toString());
            }
            return true;
        }
        case "special": {
            if (managers.isEmpty()) {
                sender.sendMessage("No managers loaded :(");
            }
            if (args.length == 1) {
                if (specialItem == null) {
                    sender.sendMessage("No special item");
                } else {
                    sender.sendMessage("Special: " + specialItem.getType()
                                       + "x" + specialItem.getAmount()
                                       + " " + (specialChance * 100.0) + "%");
                }
                return true;
            }
            if (args.length > 2) return false;
            int intChance = 100;
            double chance = 1.0;
            if (args.length >= 2) {
                intChance = Integer.parseInt(args[1]);
                chance = (double) intChance * 0.01;
            }
            if (intChance == 0) {
                specialItem = null;
                specialChance = 0;
                saveSpecialItem();
                sender.sendMessage("Special item removed.");
                return true;
            }
            if (player == null) {
                sender.sendMessage("Player expected");
                return true;
            }
            ItemStack item = player.getInventory().getItemInMainHand();
            specialItem = item.clone();
            specialChance = chance;
            saveSpecialItem();
            player.sendMessage("Special item set.");
            return true;
        } // case "special"
        default: return false;
        } // switch (args[0])
    } // onCommand

    void loadSpecialItem() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(specialItemFile);
        specialItem = cfg.getItemStack("item");
        specialChance = cfg.getDouble("chance");
    }

    void saveSpecialItem() {
        YamlConfiguration cfg = new YamlConfiguration();
        if (specialItem != null) {
            cfg.set("item", specialItem);
            cfg.set("chance", specialChance);
        }
        try {
            cfg.save(specialItemFile);
        } catch (IOException ioe) {
            getLogger().log(Level.SEVERE, "Saving special item", ioe);
        }
    }
}
