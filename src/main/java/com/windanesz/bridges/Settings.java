package com.windanesz.bridges;

import com.windanesz.bridges.worldgen.WoodSet;
import com.windanesz.bridges.worldgen.WorldGenBridge;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Arrays;
import java.util.Locale;

// None of it needs a restart, and it only changes new chunks
@Config(modid = DansBridges.MODID, name = "DansBridges")
public final class Settings {

    @Config.Name("General Settings")
    @Config.LangKey("settings." + DansBridges.MODID + ":general_settings")
    public static GeneralSettings generalSettings = new GeneralSettings();

    @Config.Name("Bridge Types")
    @Config.LangKey("settings." + DansBridges.MODID + ":bridge_type_settings")
    public static BridgeTypeSettings bridgeTypeSettings = new BridgeTypeSettings();

    @Config.Comment("Blocks of other mods that the bridges use when one of those mods is installed.")
    @Config.Name("Blocks from Other Mods")
    @Config.LangKey("settings." + DansBridges.MODID + ":block_settings")
    public static BlockSettings blockSettings = new BlockSettings();

    private Settings() {
        // no instances
    }

    public static ResourceLocation[] toResourceLocations(String... strings) {
        return Arrays.stream(strings).filter(s -> s != null && !s.trim().isEmpty()).map(s -> new ResourceLocation(s.toLowerCase(Locale.ROOT).trim())).toArray(ResourceLocation[]::new);
    }

    public static class GeneralSettings {

        @Config.Comment("Build bridges across rivers: arched stone bridges, stone bridges with railings and a lantern under the arch, humped stone bridges, small stone footbridges, great cobblestone bridges, plain wooden bridges, wooden bridges with railings and stone gates, and rickety ones. Their kind and their wood come from the biome on the bank. Default: true")
        @Config.Name("Enabled")
        public boolean enabled = true;

        @Config.Comment("How far apart the bridges are: the world is cut into squares of this many chunks, and each square can have one, where a river runs through it. Default: 8")
        @Config.Name("Spacing")
        @Config.RangeInt(min = 4, max = 64)
        public int spacing = 8;

        @Config.Comment("How many of the squares that a river runs through get a bridge, in percent. Default: 60")
        @Config.Name("Chance")
        @Config.RangeInt(min = 0, max = 100)
        public int chance = 60;

        @Config.Comment("The narrowest river that a bridge is built across, in blocks of water. Default: 4")
        @Config.Name("Narrowest River")
        @Config.RangeInt(min = 2, max = 48)
        public int minWidth = 4;

        @Config.Comment("The widest river that a bridge is built across, in blocks of water. Default: 32")
        @Config.Name("Widest River")
        @Config.RangeInt(min = 2, max = 48)
        public int maxWidth = 32;

        @Config.Comment("How many of the bridges are broken, in percent: a stretch of the deck over the middle of the river has fallen in, with ragged edges, stumps of the piers and posts left in the water, and some of the rubble on the river bed. Only bridges over rivers of 6 blocks or more break. Default: 25")
        @Config.Name("Broken Bridges")
        @Config.RangeInt(min = 0, max = 100)
        public int brokenChance = 25;

        @Config.Comment("The dimensions, by number, that bridges are built in: 0 is the Overworld, -1 the Nether and 1 the End. Leave it empty for every dimension. They are only built over rivers, and never in flat worlds or with structures turned off. Default: 0")
        @Config.Name("Dimensions")
        public int[] dimensions = new int[]{0};
    }

    public static class BridgeTypeSettings {

        @Config.Comment("Arched stone bridges, of stone bricks with moss and vines, or of sandstone in deserts and red sandstone in mesas.")
        @Config.Name("Arched Stone Bridges")
        public TypeSettings stoneArchBridges = new TypeSettings();

        @Config.Comment("Stone bridges with posts and wooden railings, and a lantern hanging under the arch.")
        @Config.Name("Stone Bridges with Railings")
        public TypeSettings stoneRailedBridges = new TypeSettings();

        @Config.Comment("Humped stone bridges on two or three arches, of stone bricks faced with polished andesite and cobblestone, with no railing.")
        @Config.Name("Humped Stone Bridges")
        public TypeSettings stoneHumpedBridges = new TypeSettings();

        @Config.Comment("Small stone footbridges on low arches over rivers of up to 14 blocks, with low walls, buttresses and a light on each corner post.")
        @Config.Name("Small Stone Bridges")
        public TypeSettings stoneSmallBridges = new TypeSettings();

        @Config.Comment("Great humped cobblestone bridges on wide arches over rivers of 12 blocks or more, with a parapet, buttresses and torches.")
        @Config.Name("Cobblestone Bridges")
        public TypeSettings cobblestoneBridges = new TypeSettings();

        @Config.Comment("Plain wooden bridges of planks and logs, low over the water.")
        @Config.Name("Wooden Bridges")
        public TypeSettings woodenBridges = new TypeSettings();

        @Config.Comment("Wooden bridges with railings, posts and gates of cobblestone.")
        @Config.Name("Wooden Bridges with Railings")
        public TypeSettings woodenRailedBridges = new TypeSettings();

        @Config.Comment("Rickety wooden bridges on stilts of fences, with planks missing and loose.")
        @Config.Name("Rickety Bridges")
        public TypeSettings ricketyBridges = new TypeSettings();
    }

    public static class TypeSettings {

        @Config.Comment("How common this type is next to the others. It scales the weight each biome gives the type, so 200 makes it twice as likely and 0 turns it off. Default: 100")
        @Config.Name("Weight")
        @Config.RangeInt(min = 0, max = 1000)
        public int weight = 100;

        @Config.Comment("The bank biomes this type is built in, by registry name, e.g. minecraft:plains. Leave it empty for every biome.")
        @Config.Name("Biome Whitelist")
        public String[] biomes = new String[0];
    }

    public static class BlockSettings {

        @Config.Comment("Hang lanterns under the arches of the stone bridges with railings, and stand them on the posts at their ends. Needs a lantern from another mod (see below), and a chain for the longer ones. Default: true")
        @Config.Name("Hang Lanterns")
        public boolean hangLanterns = true;

        @Config.Comment("The lanterns to hang under the arches, by registry name. The first one that is in the game is used, and none is hung without one. A lantern must hang by a property named \"hanging\".")
        @Config.Name("Lanterns")
        public String[] lanterns = new String[]{"sisyphus:lantern", "futuremc:lantern"};

        @Config.Comment("The chains that the lanterns hang from in the higher arches, by registry name. The first one that is in the game is used; without one, a lantern only hangs right under an arch.")
        @Config.Name("Chains")
        public String[] chains = new String[]{"sisyphus:chain", "futuremc:chain"};

        @Config.Comment("The mossy stone brick stairs of the arched stone bridges, by registry name. The first one that is in the game is used; without one, the stairs are all plain stone brick stairs.")
        @Config.Name("Mossy Stone Brick Stairs")
        public String[] mossyStoneBrickStairs = new String[]{"sisyphus:mossy_stone_brick_stairs"};

        @Config.Comment("Build the wooden parts of the bridges of the wood of the trees on the bank, from any mod whose recipes make planks, slabs, stairs and fences of it. Where there are no trees, or none like that, the biome's vanilla wood is used. Default: true")
        @Config.Name("Wood of Nearby Trees")
        public boolean treeWood = true;

        @Config.Comment("Logs whose wood is never used for the bridges, by registry name, e.g. the logs of a mod's rubber trees.")
        @Config.Name("Ignored Logs")
        public String[] ignoredLogs = new String[0];

        @Config.Comment("The wood of the bridges in a biome, used instead of the wood of the trees there. One line per biome: <biome> = <planks>, <log>, <slab>, <stairs>, <fence>, with the properties of a block in square brackets the way /setblock takes them. Lines for mods that aren't installed are left out. For example: biomesoplenty:redwood_forest = biomesoplenty:planks_0[variant=redwood], biomesoplenty:log_2[variant=redwood], biomesoplenty:redwood_slab, biomesoplenty:redwood_stairs, biomesoplenty:redwood_fence")
        @Config.Name("Biome Woods")
        public String[] biomeWoods = new String[0];
    }

    @SuppressWarnings("unused")
    @Mod.EventBusSubscriber(modid = DansBridges.MODID)
    private static class EventHandler {
        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(DansBridges.MODID)) {
                ConfigManager.sync(DansBridges.MODID, Config.Type.INSTANCE);
                WorldGenBridge.clearPlans();
                WoodSet.clearOverrides();
            }
        }
    }
}
