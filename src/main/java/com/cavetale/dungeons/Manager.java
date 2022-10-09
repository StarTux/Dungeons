package com.cavetale.dungeons;

import com.cavetale.core.event.player.PluginPlayerEvent;
import com.cavetale.core.struct.Cuboid;
import com.cavetale.core.struct.Vec3i;
import com.cavetale.mytems.Mytems;
import com.cavetale.structure.cache.Structure;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;
import org.bukkit.persistence.PersistentDataType;
import static com.cavetale.core.exploits.PlayerPlacedBlocks.isPlayerPlaced;
import static com.cavetale.dungeons.DungeonsPlugin.DUNGEON_KEY;
import static com.cavetale.dungeons.DungeonsPlugin.dungeonsPlugin;
import static com.cavetale.structure.StructurePlugin.structureCache;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@Getter @RequiredArgsConstructor
final class Manager implements Listener {
    private final String worldName;
    private Cuboid lootedBoundingBox = null;
    private Dungeon lootedDungeon = null;
    private UUID lootingPlayer = null;
    private final Random random = ThreadLocalRandom.current();

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        onPlayerInteractChest(event);
        onPlayerInteractCompass(event);
    }

    @EventHandler(ignoreCancelled = true)
    protected void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!block.getWorld().getName().equals(worldName)) return;
        if (block.getType() == Material.STONE) return;
        Structure structure = structureCache().at(block);
        if (structure == null || !DUNGEON_KEY.equals(structure.getKey())) return;
        Dungeon dungeon = structure.getJsonData(Dungeon.class, Dungeon::new);
        if (dungeon.isDiscovered()) return;
        dungeon.setDiscovered(true);
        structure.saveJsonData();
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
        if (!block.getWorld().getName().equals(worldName)) return;
        if (isPlayerPlaced(block)) return;
        BlockState state = block.getState();
        if (!(state instanceof Chest chest)) return;
        // Check and update structure
        Structure structure = structureCache().at(block);
        if (structure == null || !DUNGEON_KEY.equals(structure.getKey())) return;
        Dungeon dungeon = structure.getJsonData(Dungeon.class, Dungeon::new);
        if (dungeon.isRaided()) return;
        dungeon.setRaided(true);
        structure.saveJsonData();
        // Do the loot
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
            // These contain treasure maps:
            if (it == LootTables.SHIPWRECK_MAP) continue;
            if (it == LootTables.UNDERWATER_RUIN_BIG) continue;
            if (it == LootTables.UNDERWATER_RUIN_SMALL) continue;
            lootTables.add(it.getLootTable());
        }
        LootTable lootTable = !lootTables.isEmpty()
            ? lootTables.get(random.nextInt(lootTables.size()))
            : LootTables.SIMPLE_DUNGEON.getLootTable();
        dungeonsPlugin().getLogger().info("Loot table: " + lootTable.getKey());
        try {
            lootedDungeon = dungeon;
            lootedBoundingBox = structure.getBoundingBox();
            lootingPlayer = player.getUniqueId();
            lootTable.fillInventory(inventory, random, context);
        } finally {
            lootedDungeon = null;
            lootedBoundingBox = null;
            lootingPlayer = null;
        }
        PluginPlayerEvent.Name.DUNGEON_LOOT.call(dungeonsPlugin(), player);
    }

    @EventHandler(ignoreCancelled = true)
    private void onLootGenerate(LootGenerateEvent event) {
        if (lootedDungeon == null) return;
        List<ItemStack> loot = event.getLoot();
        loot.removeIf(it -> it != null && it.getType() == Material.FILLED_MAP);
        new DungeonLootEvent(event.getLootContext().getLocation().getBlock(),
                             Bukkit.getPlayer(lootingPlayer),
                             lootedDungeon,
                             lootedBoundingBox,
                             loot).callEvent();
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
            dungeonsPlugin().getLogger().info("Bonus item: " + mat + "x" + amount);
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
            dungeonsPlugin().getLogger().info("Bonus item: Enchanted book: " + ench.getKey().getKey() + " " + level);
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
        if (!player.getWorld().getName().equals(worldName)) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.COMPASS) return;
        Location location = player.getLocation();
        if (location.getBlockY() > 48) {
            player.sendMessage(text("You are too high up to locate dungeons", RED));
            player.playSound(player.getEyeLocation(),
                             Sound.UI_BUTTON_CLICK, SoundCategory.MASTER,
                             0.5f, 2.0f);
            return;
        }
        final int margin = 100;
        List<Structure> structures = structureCache().within(worldName,
                                                             new Cuboid(location.getBlockX() - margin,
                                                                        location.getWorld().getMinHeight(),
                                                                        location.getBlockZ() - margin,
                                                                        location.getBlockX() + margin,
                                                                        location.getWorld().getMaxHeight(),
                                                                        location.getBlockZ() + margin),
                                                             DUNGEON_KEY);
        if (structures.isEmpty()) {
            player.sendMessage(text("There are no dungeons in range", RED));
            return;
        }
        Structure structure = null;
        int minDistance = Integer.MAX_VALUE;
        final Vec3i playerVec = Vec3i.of(location);
        for (Structure it : structures) {
            Dungeon dungeon = structure.getJsonData(Dungeon.class, Dungeon::new);
            if (dungeon.isDiscovered() || dungeon.isRaided()) continue;
            Vec3i dungeonVec = it.getBoundingBox().getCenter();
            int dist = dungeonVec.distanceSquared(playerVec);
            if (dist < minDistance) {
                structure = structure;
                minDistance = dist;
            }
        }
        if (structure == null) {
            player.sendMessage(text("There are no dungeons in range", RED));
            return;
        }
        Cuboid boundingBox = structure.getBoundingBox();
        int tx = (boundingBox.ax + boundingBox.bx) / 2;
        int tz = (boundingBox.az + boundingBox.bz) / 2;
        int dx = tx - location.getBlockX();
        int dz = tz - location.getBlockZ();
        Location target = new Location(location.getWorld(), (double) tx, location.getY(), (double) tz);
        player.setCompassTarget(target);
        if (Math.abs(dx) < 8 && Math.abs(dz) < 8) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MASTER, 0.5f, 0.7f);
            player.sendMessage(text("A dungeon is very close!", GOLD));
        } else {
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, SoundCategory.MASTER, 0.5f, 2.0f);
            player.sendMessage(text("Your compass points to a nearby dungeon", GOLD));
        }
    }

    private static final EntityType[] SPAWNED_TYPES = new EntityType[] {
        EntityType.CAVE_SPIDER,
        EntityType.SPIDER,
        EntityType.ZOMBIE,
        EntityType.SKELETON,
        EntityType.CREEPER,
    };

    private static final NamespacedKey SPAWNED_KEY = NamespacedKey.fromString("dungeons:spawned");

    @EventHandler
    private void onSpawnerSpawn(SpawnerSpawnEvent event) {
        final Block block = event.getSpawner().getBlock();
        if (!block.getWorld().getName().equals(worldName)) return;
        Structure structure = structureCache().at(block);
        if (structure == null || !DUNGEON_KEY.equals(structure.getKey())) return;
        Bukkit.getScheduler().runTask(dungeonsPlugin(), () -> {
                if (!(block.getState() instanceof CreatureSpawner spawner)) return;
                int spawned = spawner.getPersistentDataContainer().getOrDefault(SPAWNED_KEY, PersistentDataType.INTEGER, 0);
                spawner.getPersistentDataContainer().set(SPAWNED_KEY, PersistentDataType.INTEGER, spawned + 1);
                EntityType spawnedType = SPAWNED_TYPES[random.nextInt(SPAWNED_TYPES.length)];
                spawner.setSpawnedType(spawnedType);
                spawner.update();
                if (spawned > 10) {
                    double blastChance = 1.0 - 10.0 / spawned;
                    if (random.nextDouble() < blastChance) {
                        dungeonsPlugin().getLogger().info("Exploding spawner at"
                                                          + " " + block.getX()
                                                          + " " + block.getY()
                                                          + " " + block.getZ());
                        block.getWorld().createExplosion(block.getLocation().add(0.5, 0.5, 0.5), 6f);
                    }
                }
            });
    }
}
