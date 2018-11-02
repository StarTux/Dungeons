package com.cavetale.dungeons;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;

@Getter @RequiredArgsConstructor
final class Manager implements Listener {
    final DungeonWorld dungeonWorld;

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        onPlayerInteractChest(event);
        onPlayerInteractCompass(event);
    }

    /**
     * Listen for chest opening to see if one ouf our dungeon chests
     * with the custom LootTable tag is being opened.
     */
    void onPlayerInteractChest(PlayerInteractEvent event) {
        if (event.isCancelled()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!block.getWorld().getName().equals(this.dungeonWorld.worldName)) return;
        BlockState state = block.getState();
        if (!(state instanceof Chest)) return;
        Chest chest = (Chest)state;
        Dungeon dungeon = this.dungeonWorld.findDungeonAt(block);
        if (dungeon == null) return;
        Player player = event.getPlayer();
        if (chest.getLootTable() != null
            && chest.getLootTable().getKey() != null
            && chest.getLootTable().getKey().toString().equals(this.dungeonWorld.lootTable)) {
            NamespacedKey newKey = NamespacedKey.minecraft("chests/simple_dungeon");
            LootTable newLootTable = Bukkit.getServer().getLootTable(newKey);
            chest.setLootTable(newLootTable);
            chest.update();
            DungeonLootEvent dungeonLootEvent = new DungeonLootEvent(block, chest.getInventory(), player, dungeon);
            Bukkit.getPluginManager().callEvent(dungeonLootEvent);
        }
        // Update dungeon raided state
        if (dungeon != null && !dungeon.isRaided()) {
            dungeon.setRaided(true);
            this.dungeonWorld.savePersistence();
        }
    }

    /**
     * Compass points toward the nearest dungeon as long as player is
     * level with or lower than it.
     */
    public void onPlayerInteractCompass(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(dungeonWorld.worldName)) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.COMPASS) return;
        Location location = player.getLocation();
        Dungeon dungeon = dungeonWorld.findNearestDungeon(location, true);
        if (dungeon == null) return;
        if (dungeon.hi.get(1) < location.getBlockY()) {
            player.sendMessage(ChatColor.RED + "You are too high up to locate dungeons.");
            player.playSound(player.getEyeLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 2.0f);
            return;
        }
        int tx = (dungeon.lo.get(0) + dungeon.hi.get(0)) / 2;
        int tz = (dungeon.lo.get(2) + dungeon.hi.get(2)) / 2;
        int dx = tx - location.getBlockX();
        int dz = tz - location.getBlockZ();
        Location target = new Location(location.getWorld(), (double)tx, location.getY(), (double)tz);
        player.setCompassTarget(target);
        if (Math.abs(dx) < 8 && Math.abs(dz) < 8) {
            player.playSound(player.getEyeLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MASTER, 0.5f, 0.7f);
            player.sendMessage(ChatColor.GOLD + "A dungeon is very close!");
        } else {
            player.playSound(player.getEyeLocation(), Sound.UI_TOAST_IN, SoundCategory.MASTER, 0.5f, 2.0f);
            player.sendMessage(ChatColor.GOLD + "Your compass points to a nearby dungeon.");
        }
    }
}
