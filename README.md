# Dungeons

Generate and run custom dungeons.  This plugin has a generator and a game server component.  The former can (but does not have to) be moved off-shore and serve during pre-generation on a separate server.

## Generator Resources
All resources are found in this plugin's data folder.  All except dungeon schematics will be saved to disk if they're not present, so deleting them before the plugins is loaded will reset their respective values to the default state.  Schematics are expected to be provided and copied into the `dungeons` folder before the generation process is triggered.  All files are loaded when the plugin is enabled.
- `dungeons/*.json` Dungeon schematic files in the [BlockClip](https://github.com/StarTux/BlockClip) format.
- `config.yml` The configuration file, see below.

## General Resources
- `WORLD_FOLDER/dungeons.json`, a JSON file containing the bounding boxes of all generated dungeons.  Produced and used during world generation to respect the dungeon margin.  At game server runtime, utilized to allow players to locate dungeons with a compass, and admins with the `/dungeons` command, see below.

## Configuration
Bukkit's standard `config.yml` file contains a few configurations which control if and how this plugin interferes with world generation.
- `generate: false` Generate new dungeons. Dungeons are only generated inside chunks while they are populated, so setting this to true only makes sense on the generator server.
- `worlds: [mine]` The worlds to generate dungeons in.
- `margin: 192` The minimum distance between any two dungeons.

## Commands
The `/dungeons` command is only intended for admins and requires the `dungeons.dungeons` permission node.
- `/dungeons locate` locates the nearest dungeon, revealing its exact center coordinates.

## Behavior
### During world generation
Whenever a chunk is [populated](https://papermc.io/javadocs/org/bukkit/event/world/ChunkPopulateEvent.html), this plugin will attempt to spawn a dungeon in it.  It will first check if the margin to the nearest existing dungeons is wide enough, then make 10 attempts to spawn a dungeon with a random block offset *(0-15, 8-47, 0-15)* within the chunk.  Each attempt will check for invalid blocks in the target area, such as water, lava, rails, chests, mossy cobblestone; in short anything that may belong with a previously generated structure.  It also ensures that the top and bottom layer of where the dungeon goes are not air.  Once everything is in order, it will paste the dungeon and register it in the `dungeons.json` file, with its bounding box and name.

Experience shows that this behavior spawns dungeons thoroughly spaced yet tightly packed, without noticeable unexpected gaps, without ever clipping unpleasantly into existing structures or being unnaturally exposed.  With the default margin of 192, each region file appears to contain 4-5 dungeons on a regular basis.  Overall, these observations are cause for great optimism.  Future world generations will yield more data.

### At game server runtime
Whenever a chest is about to be populated with the configured loot table, the `DungeonLootEvent` is called, giving all listeners the opportunity to take note of or modify player, block, and inventory.

A compass can be used by ordinary players to locate dungeons.  The compass is activated via right-click and will trigger the issuing player's compass target to be set.  The compass will refuse to work if the player is on a y-level higher than the nearest dungeon.  It will point along the cardinal axes to obfuscate the exact location somewhat; repeated clicking is therefore advised.

## Known Bugs and Planned Features
- Double chests spawn with a visual glitch where the right half appears like a single chest which clips into the double chest part.  Adjusting the pasting order should put an end to this, but more empirical data need to be collected.

## Dependencies

All dependencies are soft, because they only apply to the Generator or Manager portion, respectively:

### Generator
- [Decorator](https://github.com/StarTux/Decorator) to generate any dungeons in the world, this plugin requires Decorator.
- [BlockClip](https://github.com/StarTux/BlockClip) to produce the dungeon schematics, as well as load and paste them via the API.

### Manager
- [Exploits](https://github.com/StarTux/Exploits) to check if the interacted chest is player placed.
- [Worlds](https://github.com/StarTux/Worlds) should be loaded first to ensure the mining world is loaded.
- [Core](https://github.com/StarTux/Core) is needed because the PluginPlayerEvent is called.

### General
- [Worlds](https://github.com/StarTux/Worlds) should be loaded first to ensure the mining world is loaded.

## Links
- [Source code](https://github.com/StarTux/Dungeons) on Github

## TODO
- Remove the loot table switching in the Manager as soon as a new mining world is loaded!