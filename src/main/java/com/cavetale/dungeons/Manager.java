package com.cavetale.dungeons;

import com.cavetale.core.event.player.PluginPlayerEvent;
import com.cavetale.core.font.VanillaItems;
import com.cavetale.core.item.ItemKinds;
import com.cavetale.core.struct.Cuboid;
import com.cavetale.core.struct.Vec3i;
import com.cavetale.mytems.Mytems;
import com.cavetale.structure.cache.Structure;
import java.util.ArrayList;
import java.util.Iterator;
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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.TrialSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.entity.TrialSpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTables;
import org.bukkit.persistence.PersistentDataType;
import static com.cavetale.core.exploits.PlayerPlacedBlocks.isPlayerPlaced;
import static com.cavetale.dungeons.DungeonsPlugin.DUNGEON_KEY;
import static com.cavetale.dungeons.DungeonsPlugin.dungeonsPlugin;
import static com.cavetale.mytems.util.Text.formatDouble;
import static com.cavetale.structure.StructurePlugin.structureCache;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@Getter @RequiredArgsConstructor
final class Manager implements Listener {
    private static final int MIN_DUNGEON_LENGTH = 12;

    private final String worldName;
    private Cuboid lootedBoundingBox = null;
    private Dungeon lootedDungeon = null;
    private UUID lootingPlayer = null;
    private ItemStack lootItem = null;
    private final Random random = ThreadLocalRandom.current();

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
        final Player player = event.getPlayer();
        PluginPlayerEvent.Name.DUNGEON_LOOT.call(dungeonsPlugin(), player);
        if (structure.getBoundingBox().getSizeX() >= MIN_DUNGEON_LENGTH && structure.getBoundingBox().getSizeZ() >= MIN_DUNGEON_LENGTH) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(dungeonsPlugin(), () -> {
                    final org.bukkit.block.data.type.TrialSpawner blockData = (org.bukkit.block.data.type.TrialSpawner) Material.TRIAL_SPAWNER.createBlockData();
                    blockData.setOminous(true);
                    block.setBlockData(blockData);
                    if (block.getState() instanceof TrialSpawner trialSpawner) {
                        trialSpawner.setOminous(true);
                        trialSpawner.startTrackingPlayer(player);
                        trialSpawner.getOminousConfiguration().setBaseSimultaneousEntities(4);
                        trialSpawner.getOminousConfiguration().setBaseSpawnsBeforeCooldown(16);
                        trialSpawner.getOminousConfiguration().setSpawnRange(6);
                        trialSpawner.getOminousConfiguration().setSpawnedType(SpawnedTypes.random(random));
                        trialSpawner.update();
                    }
                    final Location center = block.getLocation().add(0.5, 0.5, 0.5);
                    block.getWorld().playSound(center, Sound.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE, 2f, 1f);
                    block.getWorld().spawnParticle(Particle.WHITE_SMOKE, center, 64, 0.35, 0.35, 0.35, 0.1);
                    block.getWorld().spawnParticle(Particle.LARGE_SMOKE, center, 32, 0.25, 0.25, 0.25, 0.1);
                });
        } else {
            // Do the loot
            Inventory inventory = chest.getInventory();
            chest.setLootTable(null);
            chest.update();
            try {
                lootedDungeon = dungeon;
                lootedBoundingBox = structure.getBoundingBox();
                lootingPlayer = player.getUniqueId();
                lootItem = getLootPoolItem(7, 7, 1);
                final LootContext lootContext = new LootContext.Builder(block.getLocation())
                    .killer(player)
                    .build();
                LootTables.SIMPLE_DUNGEON.getLootTable().fillInventory(inventory, random, lootContext);
            } finally {
                lootedDungeon = null;
                lootedBoundingBox = null;
                lootingPlayer = null;
                lootItem = null;
            }
        }
    }

    /**
     * Make sure loot cannot generate in the dungeon chests
     * prematurely, for example using a hopper.
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOW)
    private void onLootGenerateInUnlootedDungeon(LootGenerateEvent event) {
        if (!event.getWorld().getName().equals(worldName)) return;
        if (!(event.getInventoryHolder() instanceof Chest chest)) return;
        final Block block = chest.getBlock();
        Structure structure = structureCache().at(block);
        if (structure == null || !DUNGEON_KEY.equals(structure.getKey())) return;
        Dungeon dungeon = structure.getJsonData(Dungeon.class, Dungeon::new);
        if (dungeon.isRaided()) return;
        event.setCancelled(true);
        dungeonsPlugin().getLogger().info("Deny " + event.getEventName() + " " + worldName + " " + block.getX() + " " + block.getY() + " " + block.getZ());
    }

    @EventHandler(ignoreCancelled = true)
    private void onLootGenerateInLootedDungeon(LootGenerateEvent event) {
        if (lootedDungeon == null) return;
        List<ItemStack> loot = event.getLoot();
        loot.removeIf(it -> it != null && it.getType() == Material.FILLED_MAP);
        new DungeonLootEvent(event.getLootContext().getLocation().getBlock(),
                             Bukkit.getPlayer(lootingPlayer),
                             lootedDungeon,
                             lootedBoundingBox,
                             loot).callEvent();
        if (lootItem != null) {
            loot.add(lootItem);
        }
        if (random.nextInt(3) == 0) {
            loot.add(Mytems.KITTY_COIN.createItemStack());
        }
        for (int i = 0; i < 15; i += 1) {
            if (random.nextBoolean()) {
                continue;
            } else if (random.nextBoolean()) {
                loot.add(Mytems.GOLDEN_COIN.createItemStack());
            } else {
                loot.add(Mytems.SILVER_COIN.createItemStack());
            }
        }
        dungeonsPlugin().getLogger().info(event.getEventName() + " bonus item: "
                                          + lootItem.getAmount() + "x" + ItemKinds.name(lootItem)
                                          + (event.getLootContext().getKiller() instanceof Player player
                                             ? " for " + player.getName()
                                             : ""));
    }

    private ItemStack getLootPoolItem(int dudChance, int treasureChance, int rareChance) {
        final List<List<ItemStack>> pool = getLootPool(dudChance, treasureChance, rareChance);
        final List<ItemStack> pool2 = pool.get(random.nextInt(pool.size()));
        ItemStack item = pool2.get(random.nextInt(pool2.size()));
        if (item.getAmount() > 1) {
            item.setAmount(1 + random.nextInt(item.getAmount()));
        }
        return item;
    }

    private static List<List<ItemStack>> getLootPool(int dudChance, int treasureChance, int rareChance) {
        List<List<ItemStack>> result = new ArrayList<>();
        if (dudChance > 0) {
            final List<ItemStack> dud = new ArrayList<>();
            dud.add(new ItemStack(Material.SPONGE, 16));
            dud.add(new ItemStack(Material.BONE, 64));
            dud.add(new ItemStack(Material.BONE_BLOCK, 64));
            dud.add(new ItemStack(Material.GUNPOWDER, 64));
            dud.add(new ItemStack(Material.BLAZE_ROD, 64));
            dud.add(new ItemStack(Material.TNT, 64));
            dud.add(new ItemStack(Material.ARROW, 64));
            dud.add(new ItemStack(Material.WITHER_SKELETON_SKULL));
            dud.add(new ItemStack(Material.SADDLE));
            dud.add(new ItemStack(Material.GHAST_TEAR, 16));
            dud.add(Mytems.COPPER_COIN.createItemStack(64));
            dud.add(new ItemStack(Material.MANGROVE_LOG, 64));
            dud.add(new ItemStack(Material.SMALL_DRIPLEAF, 64));
            for (int i = 0; i < dudChance; i += 1) {
                result.add(dud);
            }
        }
        if (treasureChance > 0) {
            final List<ItemStack> treasure = new ArrayList<>();
            treasure.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE));
            treasure.add(new ItemStack(Material.TOTEM_OF_UNDYING));
            treasure.add(new ItemStack(Material.SHULKER_SHELL, 10));
            treasure.add(Mytems.DIAMOND_COIN.createItemStack(10));
            treasure.add(new ItemStack(Material.TINTED_GLASS, 64));
            treasure.add(new ItemStack(Material.BREEZE_ROD, 32));
            for (int i = 0; i < treasureChance; i += 1) {
                result.add(treasure);
            }
        }
        if (rareChance > 0) {
            final List<ItemStack> rare = new ArrayList<>();
            rare.add(new ItemStack(Material.NETHER_STAR));
            rare.add(new ItemStack(Material.DRAGON_EGG));
            rare.add(new ItemStack(Material.ELYTRA));
            rare.add(new ItemStack(Material.HEART_OF_THE_SEA));
            rare.add(new ItemStack(Material.HEAVY_CORE));
            rare.add(new ItemStack(Material.END_PORTAL_FRAME));
            populateLootMytems(rare);
            for (int i = 0; i < rareChance; i += 1) {
                result.add(rare);
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("Loot pool is empty dud:" + dudChance + " treasure:" + treasureChance + " rare:" + rareChance);
        }
        return result;
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
        if (Mytems.forItem(item) != null) return;
        if (item.hasItemMeta() && item.getItemMeta() instanceof CompassMeta meta) return;
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
        final int margin = 256;
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
            if (it.isDiscovered()) {
                continue;
            }
            final Vec3i dungeonVec = it.getBoundingBox().getCenter();
            final int dist = dungeonVec.distanceSquared(playerVec);
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

    private static final NamespacedKey SPAWNED_KEY = NamespacedKey.fromString("dungeons:spawned");

    @EventHandler(ignoreCancelled = false, priority = EventPriority.NORMAL)
    private void onSpawnerSpawn(SpawnerSpawnEvent event) {
        final Block block = event.getSpawner().getBlock();
        if (!block.getWorld().getName().equals(worldName)) return;
        Structure structure = structureCache().at(block);
        if (structure == null || !DUNGEON_KEY.equals(structure.getKey())) return;
        Bukkit.getScheduler().runTask(dungeonsPlugin(), () -> {
                if (!(block.getState() instanceof CreatureSpawner spawner)) return;
                int spawned = spawner.getPersistentDataContainer().getOrDefault(SPAWNED_KEY, PersistentDataType.INTEGER, 0);
                spawner.getPersistentDataContainer().set(SPAWNED_KEY, PersistentDataType.INTEGER, spawned + 1);
                final EntityType spawnedType = SpawnedTypes.random(random);
                spawner.setSpawnedType(spawnedType);
                spawner.update();
                if (spawned > 10) {
                    double blastChance = 1.0 - 10.0 / spawned;
                    if (random.nextDouble() < blastChance) {
                        dungeonsPlugin().getLogger().info("Exploding spawner at"
                                                          + " " + block.getX()
                                                          + " " + block.getY()
                                                          + " " + block.getZ());
                        block.setType(Material.AIR);
                        block.getWorld().createExplosion(block.getLocation().add(0.5, 0.5, 0.5), 6f);
                    }
                }
            });
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.NORMAL)
    private void onTrialSpawnerSpawn(TrialSpawnerSpawnEvent event) {
        final Block block = event.getTrialSpawner().getBlock();
        if (!block.getWorld().getName().equals(worldName)) return;
        Structure structure = structureCache().at(block);
        if (structure == null || !DUNGEON_KEY.equals(structure.getKey())) return;
        if (!structure.getBoundingBox().contains(event.getEntity().getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Mob mob) {
            double health = 0.0;
            double damage = 0.0;
            double armor = 0.0;
            for (Player player : event.getTrialSpawner().getTrackedPlayers()) {
                health = Math.max(health, player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                damage = Math.max(damage, player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getValue());
                armor = Math.max(armor, player.getAttribute(Attribute.GENERIC_ARMOR).getValue() * 0.75);
            }
            final AttributeInstance maxHealthAttribute = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealthAttribute != null) {
                maxHealthAttribute.setBaseValue(Math.max(health, maxHealthAttribute.getBaseValue()));
                mob.setHealth(health);
            }
            final AttributeInstance attackDamageAttribute = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
            if (attackDamageAttribute != null) {
                attackDamageAttribute.setBaseValue(Math.max(damage, attackDamageAttribute.getBaseValue()));
            }
            final AttributeInstance armorAttribute = mob.getAttribute(Attribute.GENERIC_ARMOR);
            if (armorAttribute != null) {
                armorAttribute.setBaseValue(Math.max(armor, armorAttribute.getBaseValue()));
            }
            dungeonsPlugin().getLogger().info(event.getEventName()
                                              + " health:" + formatDouble(health)
                                              + " damage:" + formatDouble(damage)
                                              + " armor:" + formatDouble(armor));
        }
        Bukkit.getScheduler().runTask(dungeonsPlugin(), () -> {
                if (!(block.getState() instanceof TrialSpawner trialSpawner)) return;
                trialSpawner.getOminousConfiguration().setSpawnedType(SpawnedTypes.random(random));
                trialSpawner.update();
            });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onEntityExplode(EntityExplodeEvent event) {
        if (!event.getEntity().getWorld().getName().equals(worldName)) return;
        onExplode(event.blockList());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onBlockExplode(BlockExplodeEvent event) {
        if (!event.getBlock().getWorld().getName().equals(worldName)) return;
        onExplode(event.blockList());
    }

    private void onExplode(Iterable<Block> blockList) {
        for (Iterator<Block> iter = blockList.iterator(); iter.hasNext();) {
            final Block block = iter.next();
            final Structure structure = structureCache().at(block);
            if (structure == null || !DUNGEON_KEY.equals(structure.getKey())) continue;
            if (block.getState() instanceof Chest) {
                iter.remove();
            }
        }
    }

    /**
     * The trial spawner has been defeated, and now the loot spawns.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    private void onBlockDispenseLoot(BlockDispenseLootEvent event) {
        final Block block = event.getBlock();
        if (!block.getWorld().getName().equals(worldName)) return;
        final Structure structure = structureCache().at(block);
        if (structure == null || !DUNGEON_KEY.equals(structure.getKey())) return;
        final Dungeon dungeon = structure.getJsonData(Dungeon.class, Dungeon::new);
        if (!dungeon.isRaided()) return;
        if (!(event.getBlock().getState() instanceof TrialSpawner trialSpawner)) return;
        Player pl = null;
        for (Player p : trialSpawner.getTrackedPlayers()) {
            pl = p;
            break;
        }
        final Player player = pl;
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(dungeonsPlugin(), () -> {
                block.setType(Material.CHEST);
                if (!(block.getState() instanceof Chest chest)) return;
                try {
                    lootedDungeon = dungeon;
                    lootedBoundingBox = structure.getBoundingBox();
                    lootingPlayer = player.getUniqueId();
                    lootItem = getLootPoolItem(1, 3, 1);
                    final LootContext lootContext = new LootContext.Builder(block.getLocation())
                        .killer(player)
                        .build();
                    LootTables.SIMPLE_DUNGEON.getLootTable().fillInventory(chest.getInventory(), random, lootContext);
                } finally {
                    lootedDungeon = null;
                    lootedBoundingBox = null;
                    lootingPlayer = null;
                    lootItem = null;
                }
            });
    }

    private static void populateLootMytems(List<ItemStack> result) {
        result.add(Mytems.RUBY_COIN.createItemStack());
        result.add(Mytems.MAGIC_CAPE.createItemStack());
        result.add(Mytems.MOBSLAYER.createItemStack());
        result.add(Mytems.BINGO_BUKKIT.createItemStack());
        result.add(Mytems.WITCH_BROOM.createItemStack());
        result.add(Mytems.BLUNDERBUSS.createItemStack());
        result.add(Mytems.CAPTAINS_CUTLASS.createItemStack());
        result.add(Mytems.ENDERBALL.createItemStack());
        result.add(Mytems.MAGNIFYING_GLASS.createItemStack());
        result.add(Mytems.FERTILIZER.createItemStack(64));
        result.add(Mytems.SNOW_SHOVEL.createItemStack());
        result.add(Mytems.SNEAKERS.createItemStack());
        result.add(Mytems.UNICORN_HORN.createItemStack());
        result.add(Mytems.SEALED_CAVEBOY.createItemStack());
        result.add(Mytems.SCISSORS.createItemStack());
        result.add(Mytems.COLORFALL_HOURGLASS.createItemStack());
        result.add(Mytems.STRUCTURE_FINDER.createItemStack());
        result.add(Mytems.DEFLECTOR_SHIELD.createItemStack());
        result.add(Mytems.COPPER_SPLEEF_SHOVEL.createItemStack());
        result.add(Mytems.DIVIDERS.createItemStack());
        result.add(Mytems.YARDSTICK.createItemStack());
        result.add(Mytems.LUMINATOR.createItemStack());
        result.add(Mytems.SCUBA_HELMET.createItemStack());
        result.add(Mytems.MINER_HELMET.createItemStack());
        result.add(Mytems.EMPTY_WATERING_CAN.createItemStack());
        result.add(Mytems.IRON_SCYTHE.createItemStack());
        result.add(Mytems.TREE_CHOPPER.createItemStack());
        result.add(Mytems.HASTY_PICKAXE.createItemStack());
    }
}
