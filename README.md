# Dungeons

Generate and run custom dungeons.  This plugin has a generator and a game server component.  The former can (but does not have to) be moved off-shore and serve during pre-generation on a separate server.

## Generator Resources
- `dungeons/*.json` Dungeon schematic files in the [BlockClip](https://github.com/StarTux/BlockClip) formats.
- `data/chest_tag.json` The data tag which will be assigned to all dungeon chests.
- `data/spawner_tag.json` The data tag which will be assinged to all dungeon spawners.
- `config.yml` The configuration file, see below.

## General Resources
- `WORLD_FOLDER/dungeons.json` [sic], a JSON file containing the bounding boxes of all generated dungeons.  Used during world generation to respect the dungeon margin, and during runtime to allow players to locate dungeons with a compass, and admins with the command.

## Configuration
- `generate: false` Generate new dungeons. Dungeons are only generated inside chunks while they are populated, so setting this to true only makes sense on the generator server.
- `worlds: [mine]` The worlds to generate dungeons in.
- `margin: 192` The minimum distance between any two dungeons.
- `loot_table: 'cavetale:chests/dungeon'` The name of the loot table used for dungeon chests.  This value must correspond with the one in the `data/chest_tag.json` file for proper behavior.

## Runtime Behavior
Whenever a chest about to be populated with the configured loot table, the `DungeonLootEvent` is called, giving all listeners the opportunity to take note of or modify player, block, and inventory.  The loot table is then replace with `minecraft:chests/simple_dungeon`, thus populating the loot found in ordinary Minecraft dungeons.

A compass can be used by ordinary players to locate dungeons.  The compass is activated via right-click and will trigger the issuing player's compass target to be set.  The compass will refuse to work if the player is on a Y level higher than the most nearby dungeon.  It will point along the cardinal axes to obfuscate the exact location somewhat; repeated clicking is therefore advised.

## Known Bugs and Planned Features
- Dungeons should have a larger memory, including if they have already been discovered.

## Dependencies
- [BlockClip](https://github.com/StarTux/BlockClip) to produce the dungeon schematics, as well as loa and paste them via the API.

## Links
- [Source code](https://github.com/StarTux/Dungeons) on Github