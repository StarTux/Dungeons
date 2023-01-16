package com.cavetale.dungeons;

import com.cavetale.core.event.dungeon.DungeonDiscoverEvent;
import com.cavetale.core.event.player.PluginPlayerEvent;
import com.cavetale.core.font.VanillaItems;
import com.cavetale.core.item.ItemKinds;
import com.cavetale.core.struct.Cuboid;
import com.cavetale.core.struct.Vec3i;
import com.cavetale.mytems.Mytems;
import com.cavetale.structure.cache.Structure;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
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
import static net.kyori.adventure.text.Component.textOfChildren;
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
        Player player = event.getPlayer();
        final boolean discovered = dungeon.isDiscovered();
        final boolean raided = dungeon.isRaided();
        if (!dungeon.getDiscoveredBy().contains(player.getUniqueId())) {
            dungeon.getDiscoveredBy().add(player.getUniqueId());
            structure.saveJsonData();
            new DungeonDiscoverEvent(player, block, raided || discovered).callEvent();
            Component message = textOfChildren(Mytems.CAVETALE_DUNGEON,
                                               text("Cavetale Dungeon", AQUA),
                                               text(" discovered!", GOLD));
            player.sendMessage(message);
            player.sendActionBar(message);
        }
        if (discovered) return;
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

    private static final int BASE_CHANCE = 3;

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
        final List<List<ItemStack>> pool = getLootPool();
        final List<ItemStack> pool2 = pool.get(random.nextInt(pool.size()));
        final ItemStack item = pool2.get(random.nextInt(pool2.size()));
        if (item.getAmount() > 1) {
            int amount = 1;
            for (int i = 1; i < item.getAmount(); i += 1) {
                if (random.nextBoolean()) amount += 1;
            }
            item.setAmount(amount);
        }
        dungeonsPlugin().getLogger().info("Bonus item: " + item.getAmount() + "x" + ItemKinds.name(item));
        loot.add(item);
    }

    private static List<List<ItemStack>> getLootPool() {
        final List<ItemStack> dud = new ArrayList<>();
        final List<ItemStack> books = new ArrayList<>();
        final List<ItemStack> treasure = new ArrayList<>();
        final List<ItemStack> rare = new ArrayList<>();
        // Random Common Items
        dud.add(new ItemStack(Material.SPONGE, 16));
        dud.add(new ItemStack(Material.BONE, 64));
        dud.add(new ItemStack(Material.BONE_BLOCK, 64));
        dud.add(new ItemStack(Material.GUNPOWDER, 64));
        dud.add(new ItemStack(Material.TNT, 64));
        dud.add(new ItemStack(Material.ARROW, 64));
        dud.add(new ItemStack(Material.WITHER_SKELETON_SKULL));
        dud.add(new ItemStack(Material.SADDLE));
        dud.add(new ItemStack(Material.GHAST_TEAR, 16));
        dud.add(Mytems.COPPER_COIN.createItemStack(64));
        // Discs
        for (Material mat : Material.values()) {
            if (mat.isRecord()) {
                dud.add(new ItemStack(mat));
            }
        }
        // Enchanted books
        for (Enchantment enchantment : Enchantment.values()) {
            if (enchantment.isCursed()) continue;
            final ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            book.editMeta(m -> {
                    final EnchantmentStorageMeta meta = (EnchantmentStorageMeta) m;
                    meta.addStoredEnchant(enchantment, enchantment.getMaxLevel(), true);
                });
            books.add(book);
        }
        // Treasure
        treasure.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE));
        treasure.add(new ItemStack(Material.TOTEM_OF_UNDYING));
        treasure.add(new ItemStack(Material.SHULKER_SHELL));
        treasure.add(Mytems.RUBY.createItemStack(8));
        treasure.add(Mytems.SILVER_COIN.createItemStack(64));
        treasure.add(Mytems.GOLDEN_COIN.createItemStack(16));
        treasure.add(Mytems.DIAMOND_COIN.createItemStack());
        // Rare
        rare.add(new ItemStack(Material.NETHER_STAR));
        rare.add(new ItemStack(Material.DRAGON_EGG));
        rare.add(new ItemStack(Material.ELYTRA));
        rare.add(Mytems.RUBY_COIN.createItemStack());
        rare.add(Mytems.KITTY_COIN.createItemStack());
        rare.add(new ItemStack(Material.HEART_OF_THE_SEA));
        return List.of(dud, books, treasure, rare);
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
            Component message = textOfChildren(text("You are too high up to locate ", RED),
                                               Mytems.CAVETALE_DUNGEON,
                                               text("Cavetale Dungeons", RED));
            player.sendMessage(message);
            player.sendActionBar(message);
            player.playSound(player.getEyeLocation(),
                             Sound.UI_BUTTON_CLICK, SoundCategory.MASTER,
                             0.5f, 2.0f);
            return;
        }
        final int margin = 160;
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
            Dungeon dungeon = it.getJsonData(Dungeon.class, Dungeon::new);
            if (dungeon.isDiscovered() || dungeon.isRaided()) continue;
            Vec3i dungeonVec = it.getBoundingBox().getCenter();
            int dist = dungeonVec.distanceSquared(playerVec);
            if (dist < minDistance) {
                structure = it;
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
            Component message = textOfChildren(text("A ", GOLD),
                                               Mytems.CAVETALE_DUNGEON,
                                               text("Cavetale Dungeon", AQUA),
                                               text(" is very close!", GOLD));
            player.sendMessage(message);
            player.sendActionBar(message);
        } else {
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, SoundCategory.MASTER, 0.5f, 2.0f);
            Component message = textOfChildren(text("Your ", GOLD),
                                               VanillaItems.COMPASS, text("Compass", AQUA),
                                               text(" points to a nearby ", GOLD),
                                               Mytems.CAVETALE_DUNGEON,
                                               text("Cavetale Dungeon", AQUA));
            player.sendMessage(message);
            player.sendActionBar(message);
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
