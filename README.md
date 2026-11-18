# Dan's Bridges

Adds naturally generated bridges across rivers to Minecraft **1.12.2**.

## Features

- 8 kinds of bridges: arched stone, stone with railings and lanterns, humped stone, small stone footbridges, great
  cobblestone, plain wooden, wooden with railings and gates, and rickety ones on stilts.
- Fitted to each river: its width, its banks, and stairs up to the deck.
- Biome variants: the bank's biome picks the kind, the stone and the moss, and the trees around it the wood, from any
  mod.
- Broken bridges, and snow on them in cold biomes.
- Configurable: how often, which rivers and dimensions, and a weight and biome whitelist for each kind.
- Custom bridges for pack makers: build a span, a pier and an end with vanilla structure blocks, from any mod's blocks,
  with loot chests and mobs. The span repeats to fit any river, ramps join the ends to the banks, and a small
  `bridge.json` sets where it appears.

An unofficial 1.12.2 reimplementation of [YUNG's Bridges](https://www.curseforge.com/minecraft/mc-mods/yungs-bridges) by
YUNGNICKYOUNG, the first mod to add naturally generated bridges to the game. It is not affiliated with YUNG, and it
contains no code or files from the original.

Bridges styles were inspired by the original bridges of YUNG's mod.

## Wood from other mods

Bridges support a dynamic material lookup for the biome around them for their woods.

Bridges are built of the wood of the trees around them, from any mod, such as the redwood of Biomes O' Plenty. A mod's
wood is used when its recipes make planks, slabs, stairs and fences of it. Where there are no trees, or none like that,
the biome's vanilla wood is used. In the config, under "Blocks from Other Mods":

- `Ignored Logs` leaves out logs that shouldn't become bridges, such as rubber trees.
- `Biome Woods` sets the wood of a biome by hand, for when the trees give the wrong one.
- `Wood of Nearby Trees` turns it off, so that only vanilla wood is used.

## Custom bridges

Pack makers can add their own bridges, built in game with vanilla structure blocks. Each bridge is a folder:

```
config/dansbridges/bridges/<bridge_id>/
    bridge.json
    span.nbt    required: the part over the water, repeated across the river
    pier.nbt    optional: placed between spans
    end.nbt     optional: placed on each bank, turned around for the far one
```

### Building the pieces

Apologies for this text art but I think it conveys the information well enough.
How the pieces line up across a river, seen from the side:

```
   end      span     pier     span      end
+-------+ +-------+ +----+ +-------+ +-------+
|  -->  | | /---\ | |    | | /---\ | |  <--  |   the far end is the same piece, turned around
+-------+ +-------+ +----+ +-------+ +-------+
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~   water surface = bottom layer of every piece
    |                |  |                |        solid blocks in the bottom layer of
    |                |  |                |        piers and ends go down to the ground
###############################################   river bed and banks
```

The span repeats as often as the river needs, with a pier between each two.

Each piece seen from above:

```
                 north
        +-----------------------+
        |                       |
  west  |  ===== walk ====> +X  |  east     at most 11 blocks wide,
        |                       |           centred on the middle row
        +-----------------------+
                 south
```

**Size and direction**

- Build running east (+X), with the width along Z, centred on the middle row.
- At most 11 wide and 32 high. Spans and ends at most 16 long, piers at most 8. All pieces the same width.
- The end is the west bank: its east edge joins the span.

**Heights**

- The bottom layer of every piece is the water surface.
- The walkway (the lowest block in the middle row with two air above it) is at the same height in the span, the pier and
  the end's east edge.
- Give the end's west edge a walkway too. A ramp joins it to the bank; if that takes more than 12 blocks, the bridge
  isn't built.

**Blocks**

- Air clears the world, except water. Structure void keeps it.
- Solid blocks in the bottom layer of piers and ends reach down to the ground.
- Any mod's blocks work. If a mod is missing, the bridge is skipped and the log says why.

**Markers** (structure blocks in Data mode)

- `chest:<loot table>` - a chest with that loot, e.g. `chest:minecraft:chests/simple_dungeon`.
- `entity:<entity id>` - spawns that entity, e.g. `entity:minecraft:villager`. NBT may follow:
  `entity:minecraft:zombie {CustomName:"Troll"}`. Mobs are made persistent.
- To keep entities exactly as built, such as armor stands, save the piece with "Include entities".

### bridge.json

Each subfolder needs a `bridge.json` file, which sets the rules for where the bridge is built. The example below is a
toll bridge that appears in plains and forests, over rivers 6 to 24 blocks wide, with the wood of the biome, and can
appear broken.

```json
{
    "weight": 100,
    "biomes": [
        "minecraft:plains",
        "minecraft:forest"
    ],
    "min_river": 6,
    "max_river": 24,
    "biome_wood": true,
    "can_break": true
}
```

- `weight` - how common it is. Built-in kinds have 100 to 300 where they suit. 0 turns it off.
- `biomes` - the bank biomes it appears in. Empty means all.
- `min_river`, `max_river` - the river widths, in blocks.
- `biome_wood` - replaces vanilla oak with the local wood. Doors and fence gates only change to vanilla woods.
- `can_break` - whether it can appear broken.

All fields are optional. By default the weight is 100, every biome and width is allowed, and the rest are off.

### Example

On first launch, `config/dansbridges/bridges/example_toll_bridge/` is created with a working bridge (weight 100; plains,
savannas, forests, taigas). Load its pieces in a structure block to see how it is built:

- `span.nbt` - wooden deck with railings.
- `pier.nbt` - log posts reaching the river bed.
- `end.nbt` - a gatehouse with a loot chest marker, a toll keeper marker and a saved armor stand.

Set its weight to 0 or delete the folder to remove it. It isn't recreated.

### Testing

- `/dansbridges place <bridge_id> [broken]` - stand on a bank and look across the river. Ignores weight and biomes, not
  river widths. Built-in kinds work too, e.g. `rickety`, `stone_arch`.
- The files are read when a world starts.
- A bridge that breaks a rule is skipped, and the log names the file and the rule.

## Credits

The original mod idea by YUNGNICKYOUNG.
