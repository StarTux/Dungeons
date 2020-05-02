package com.cavetale.dungeons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.StructureType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootTable;

@Getter @RequiredArgsConstructor
final class Manager implements Listener {
    final DungeonWorld dungeonWorld;
    ItemStack specialItem;
    double specialChance;

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        onPlayerInteractChest(event);
        onPlayerInteractCompass(event);
    }

    boolean isDungeonLootChest(Chest chest) {
        LootTable lootTable = chest.getLootTable();
        if (lootTable == null) return false;
        NamespacedKey key = lootTable.getKey();
        if (key == null) return false;
        return key.toString().equals(dungeonWorld.lootTable);
    }

    /**
     * Listen for chest opening to see if one ouf our dungeon chests
     * with the custom LootTable tag is being opened.
     */
    void onPlayerInteractChest(PlayerInteractEvent event) {
        if (event.isCancelled()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
            && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!block.getWorld().getName().equals(dungeonWorld.worldName)) return;
        BlockState state = block.getState();
        if (!(state instanceof Chest)) return;
        final Chest chest = (Chest) state;
        Dungeon dungeon = dungeonWorld.findDungeonAt(block);
        if (dungeon == null) return;
        Player player = event.getPlayer();
        if (isDungeonLootChest(chest)) {
            NamespacedKey newKey = NamespacedKey.minecraft("chests/simple_dungeon");
            LootTable newLootTable = Bukkit.getServer().getLootTable(newKey);
            chest.setLootTable(newLootTable);
            chest.update();
            DungeonLootEvent dungeonLootEvent =
                new DungeonLootEvent(block, chest.getInventory(),
                                     player, dungeon);
            Bukkit.getPluginManager().callEvent(dungeonLootEvent);
        }
        // Update dungeon raided state
        if (dungeon != null && !dungeon.isRaided()) {
            dungeon.setRaided(true);
            dungeonWorld.savePersistence();
            dungeonWorld.plugin.getServer().getScheduler()
                .runTask(dungeonWorld.plugin, () -> addBonusLoot(chest));
        }
    }

    void addBonusLoot(Chest chest) {
        Random random = ThreadLocalRandom.current();
        Inventory inv = chest.getInventory();
        List<Integer> slots = new ArrayList<>(inv.getSize());
        for (int i = 0; i < inv.getSize(); i += 1) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getAmount() == 0
                || item.getType() == Material.AIR) {
                slots.add(i);
            }
        }
        if (slots.isEmpty()) return;
        Collections.shuffle(slots, random);
        final int slot = slots.get(0);
        if (slots.size() >= 2 && specialItem != null && specialItem.getAmount() > 0) {
            if (random.nextDouble() < specialChance) {
                int slot2 = slots.get(1);
                inv.setItem(slot2, specialItem.clone());
            }
        }
        final ItemStack item;
        switch (random.nextInt(3)) {
        case 0: {
            // Simple item
            Map<Material, Integer> map = new EnumMap<>(Material.class);
            for (Material mat : Material.values()) {
                if (mat.isRecord()) map.put(mat, 1);
            }
            map.put(Material.ENCHANTED_GOLDEN_APPLE, 1);
            map.put(Material.WITHER_ROSE, 32);
            map.put(Material.SPONGE, 4);
            map.put(Material.WET_SPONGE, 4);
            map.put(Material.HEART_OF_THE_SEA, 1);
            map.put(Material.NAUTILUS_SHELL, 8);
            map.put(Material.NETHER_STAR, 1);
            map.put(Material.WITHER_SKELETON_SKULL, 1);
            map.put(Material.DRAGON_EGG, 1);
            map.put(Material.SHULKER_SHELL, 4);
            map.put(Material.SCUTE, 5);
            List<Material> list = new ArrayList<>(map.keySet());
            Material mat = list.get(random.nextInt(list.size()));
            int amount = map.get(mat);
            if (amount > 1) amount -= random.nextInt(amount);
            item = new ItemStack(mat, amount);
            dungeonWorld.plugin.getLogger()
                .info("Bonus item: " + mat + "x" + amount);
            break;
        }
        case 1: {
            // Enchanted book
            List<Enchantment> list = Arrays.asList(Enchantment.values());
            Enchantment ench = list.get(random.nextInt(list.size()));
            int level = ench.getMaxLevel();
            item = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            meta.addStoredEnchant(ench, level, true);
            item.setItemMeta(meta);
            dungeonWorld.plugin.getLogger()
                .info("Bonus item: Enchanted book: " + ench + " " + level);
            break;
        }
        case 2: {
            // Treasure Map
            List<StructureType> list = Arrays
                .asList(StructureType.OCEAN_MONUMENT,
                        StructureType.BURIED_TREASURE);
            int radius = 1024;
            boolean findUnexplored = true;
            World world = chest.getBlock().getWorld();
            Location loc = chest.getBlock().getLocation();
            StructureType structureType = null;
            for (StructureType type : list) {
                Location found = world
                    .locateNearestStructure(loc, type,
                                            radius, findUnexplored);
                if (found != null) {
                    structureType = type;
                    break;
                }
            }
            if (structureType == null) return;
            try {
                item = dungeonWorld.plugin.getServer()
                    .createExplorerMap(world, loc,
                                       structureType, radius, findUnexplored);
            } catch (Exception e) {
                dungeonWorld.plugin.getLogger()
                    .warning("createExplorerMap: " + structureType
                             + " radius=" + radius
                             + " findUnexplored=" + findUnexplored);
                e.printStackTrace();
                return;
            }
            if (item == null) return;
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(Stream.of(structureType.getName().split("_"))
                                .map(s -> s.substring(0, 1).toUpperCase()
                                     + s.substring(1).toLowerCase())
                                .collect(Collectors.joining(" ")));
            item.setItemMeta(meta);
            dungeonWorld.plugin.getLogger()
                .info("Bonus item: Treasure map: " + structureType.getName());
            break;
        }
        default: return;
        }
        inv.setItem(slot, item);
    }

    /**
     * Compass points toward the nearest dungeon as long as player is
     * level with or lower than it.
     */
    public void onPlayerInteractCompass(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
            && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(dungeonWorld.worldName)) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.COMPASS) return;
        Location location = player.getLocation();
        Dungeon dungeon = dungeonWorld.findNearestDungeon(location, true);
        if (dungeon == null) return;
        if (dungeon.hi.get(1) < location.getBlockY()) {
            player.sendMessage(ChatColor.RED + "You are too high up to locate dungeons.");
            player.playSound(player.getEyeLocation(),
                             Sound.UI_BUTTON_CLICK, SoundCategory.MASTER,
                             0.5f, 2.0f);
            return;
        }
        int tx = (dungeon.lo.get(0) + dungeon.hi.get(0)) / 2;
        int tz = (dungeon.lo.get(2) + dungeon.hi.get(2)) / 2;
        int dx = tx - location.getBlockX();
        int dz = tz - location.getBlockZ();
        Location target = new Location(location.getWorld(),
                                       (double) tx, location.getY(), (double) tz);
        player.setCompassTarget(target);
        if (Math.abs(dx) < 8 && Math.abs(dz) < 8) {
            player.playSound(player.getEyeLocation(),
                             Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MASTER,
                             0.5f, 0.7f);
            player.sendMessage(ChatColor.GOLD + "A dungeon is very close!");
        } else {
            player.playSound(player.getEyeLocation(),
                             Sound.UI_TOAST_IN, SoundCategory.MASTER,
                             0.5f, 2.0f);
            player.sendMessage(ChatColor.GOLD
                               + "Your compass points to a nearby dungeon.");
        }
    }
}
