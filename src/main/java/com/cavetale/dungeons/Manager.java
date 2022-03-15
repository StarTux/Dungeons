package com.cavetale.dungeons;

import com.cavetale.core.event.player.PluginPlayerEvent;
import com.cavetale.mytems.Mytems;
import com.winthier.exploits.Exploits;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;

@Getter @RequiredArgsConstructor
final class Manager implements Listener {
    final DungeonWorld dungeonWorld;
    private Dungeon lootedDungeon = null;

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        onPlayerInteractChest(event);
        onPlayerInteractCompass(event);
    }

    /**
     * Listen for chest opening to see if one ouf our dungeon chests
     * with the custom LootTable tag is being opened.
     *
     * Policy: The first chest opened within any dungeon will be
     * populated with bonus loot.  The DungeonLootEvent and
     * PluginPlayerEvent are called.  After that, the dungeon will be
     * marked as looted, so that all additional chests will not
     * contain bonus loot.
     */
    protected void onPlayerInteractChest(PlayerInteractEvent event) {
        if (event.useInteractedBlock() == Event.Result.DENY) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!block.getWorld().getName().equals(dungeonWorld.worldName)) return;
        if (Exploits.isPlayerPlaced(block)) return;
        BlockState state = block.getState();
        if (!(state instanceof Chest chest)) return;
        Dungeon dungeon = dungeonWorld.findDungeonAt(block);
        if (dungeon == null || dungeon.isRaided()) return;
        dungeon.setRaided(true);
        dungeonWorld.savePersistence();
        Player player = event.getPlayer();
        Inventory inventory = chest.getInventory();
        chest.setLootTable(null);
        chest.update();
        LootContext context = new LootContext.Builder(block.getLocation())
            .killer(player)
            .build();
        List<LootTable> lootTables = new ArrayList<>();
        for (LootTables it : LootTables.values()) {
            if (!it.getKey().getKey().startsWith("chests/")) continue;
            if (it == LootTables.JUNGLE_TEMPLE_DISPENSER) continue;
            if (it == LootTables.SPAWN_BONUS_CHEST) continue;
            lootTables.add(it.getLootTable());
        }
        Random random = ThreadLocalRandom.current();
        LootTable lootTable = !lootTables.isEmpty()
            ? lootTables.get(random.nextInt(lootTables.size()))
            : LootTables.SIMPLE_DUNGEON.getLootTable();
        dungeonWorld.plugin.getLogger().info("Loot table: " + lootTable.getKey());
        try {
            lootedDungeon = dungeon;
            lootTable.fillInventory(inventory, random, context);
        } finally {
            lootedDungeon = null;
        }
        PluginPlayerEvent.Name.DUNGEON_LOOT.call(dungeonWorld.plugin, player);
    }

    @EventHandler(ignoreCancelled = true)
    private void onLootGenerate(LootGenerateEvent event) {
        if (lootedDungeon == null) return;
        List<ItemStack> loot = event.getLoot();
        loot.removeIf(it -> it != null && it.getType() == Material.FILLED_MAP);
        new DungeonLootEvent(event.getLootContext().getLocation().getBlock(),
                             (Player) event.getLootContext().getKiller(),
                             lootedDungeon,
                             loot).callEvent();
        Random random = ThreadLocalRandom.current();
        if (random.nextDouble() < 0.3) {
            loot.add(Mytems.KITTY_COIN.createItemStack());
        }
        final ItemStack item;
        switch (random.nextInt(2)) {
        case 0: {
            // Simple item
            Map<Material, Integer> map = new EnumMap<>(Material.class);
            for (Material mat : Material.values()) {
                if (mat.isRecord()) map.put(mat, 1);
            }
            map.put(Material.ENCHANTED_GOLDEN_APPLE, 1);
            map.put(Material.WITHER_ROSE, 16);
            map.put(Material.SPONGE, 16);
            map.put(Material.WET_SPONGE, 4);
            map.put(Material.HEART_OF_THE_SEA, 1);
            map.put(Material.NAUTILUS_SHELL, 8);
            map.put(Material.NETHER_STAR, 1);
            map.put(Material.WITHER_SKELETON_SKULL, 1);
            map.put(Material.DRAGON_EGG, 1);
            map.put(Material.SHULKER_SHELL, 4);
            map.put(Material.SCUTE, 5);
            map.put(Material.ELYTRA, 1);
            map.put(Material.TOTEM_OF_UNDYING, 1);
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
                .info("Bonus item: Enchanted book: " + ench.getKey().getKey() + " " + level);
            break;
        }
        default: return;
        }
        loot.add(item);
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
