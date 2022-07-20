package com.cavetale.dungeons;

import com.cavetale.blockclip.BlockClip;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class DungeonsPlugin extends JavaPlugin {
    @Getter protected static DungeonsPlugin instance;
    private final ArrayList<Generator> generators = new ArrayList<>();
    private final ArrayList<Manager> managers = new ArrayList<>();
    public static final NamespacedKey DUNGEON_KEY = NamespacedKey.fromString("dungeons:dungeon");

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        reloadConfig();
        saveDefaultConfig();
        int margin = getConfig().getInt("margin");
        for (String worldName : getConfig().getStringList("worlds")) {
            if (getConfig().getBoolean("manage")) {
                Manager manager = new Manager(worldName);
                getServer().getPluginManager().registerEvents(manager, this);
                managers.add(manager);
            }
            if (getConfig().getBoolean("generate")) {
                Generator generator = new Generator(worldName, margin);
                int dc = generator.loadDungeons();
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;
        if (args.length == 0) return false;
        switch (args[0]) {
        case "paste": {
            File dir = new File(getDataFolder(), "dungeons");
            File file = new File(dir, args[1] + ".json");
            if (!file.exists()) {
                sender.sendMessage("Not found: " + file);
                return true;
            }
            BlockClip clip;
            try {
                clip = BlockClip.load(file);
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
            Generator.pasteDungeon(clip, player.getLocation().getBlock(), ThreadLocalRandom.current());
            return true;
        }
        default: return false;
        }
    }

    public static DungeonsPlugin dungeonsPlugin() {
        return instance;
    }
}
