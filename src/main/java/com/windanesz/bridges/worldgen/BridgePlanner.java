package com.windanesz.bridges.worldgen;

import com.windanesz.bridges.Settings;
import net.minecraft.block.BlockPlanks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraftforge.common.BiomeDictionary;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

// Plans from the biomes alone, which are known before any chunk of the bridge is generated
final class BridgePlanner {

    private static final int SCAN = 32;
    private static final int SLACK = 20;
    private static final int TRIES = 10;
    // The room a custom bridge's plan may need beyond what the built-in ones do
    private static final int CUSTOM_REACH = 28;

    private BridgePlanner() {
    } // no instances

    static int halfLength(int maxWidth) {
        return (maxWidth + 4) / 2 + SLACK + CUSTOM_REACH;
    }

    @Nullable
    static BridgePlan plan(World world, int squareX, int squareZ, Settings.GeneralSettings settings) {
        Random random = new Random(world.getSeed() + squareX * 341873128712L + squareZ * 132897987541L + 0x6272696467L);
        if (random.nextInt(100) >= settings.chance) {
            return null;
        }
        int size = settings.spacing * 16;
        // Away from the edges of the square, so that the bridges of two squares do not meet
        int inset = Math.min(24, size / 4);
        int cellX = (squareX * size + inset) >> 2;
        int cellZ = (squareZ * size + inset) >> 2;
        int cells = (size - 2 * inset) >> 2;
        BiomeProvider provider = world.getBiomeProvider();
        Biome[] grid = provider.getBiomesForGeneration(null, cellX, cellZ, cells, cells);
        List<Integer> rivers = new ArrayList<>();
        for (int i = 0; i < cells * cells; i++) {
            if (isRiver(grid[i])) {
                rivers.add(i);
            }
        }
        Collections.shuffle(rivers, random);
        for (int i = 0; i < Math.min(TRIES, rivers.size()); i++) {
            int cell = rivers.get(i);
            int x = (cellX + cell % cells) * 4 + 2;
            int z = (cellZ + cell / cells) * 4 + 2;
            Crossing alongX = measure(provider, x, z, true, settings.maxWidth);
            Crossing alongZ = measure(provider, x, z, false, settings.maxWidth);
            Crossing crossing = alongX == null ? alongZ : alongZ == null || alongX.width() <= alongZ.width() ? alongX : alongZ;
            // The river must run on along the other axis, or the line follows a bank or cuts across a bend
            Crossing other = crossing == alongX ? alongZ : alongX;
            if (crossing != null && (other == null || other.width() >= crossing.width() * 2) && flowsAcross(provider, x, z, crossing)) {
                return plan(crossing, random, settings);
            }
        }
        return null;
    }

    @Nullable
    private static BridgePlan plan(Crossing crossing, Random random, Settings.GeneralSettings settings) {
        Biome bank = random.nextBoolean() ? crossing.before : crossing.after;
        Palette palette = Palette.of(bank);
        List<CustomBridge> customs = CustomBridge.all();
        int choice = palette.pick(random, crossing.width(), bank, customs);
        if (choice < 0) {
            return null;
        }
        CustomBridge custom = choice < palette.weights.length ? null : customs.get(choice - palette.weights.length);
        BridgeStyle style = custom == null ? BridgeStyle.values()[choice] : BridgeStyle.CUSTOM;
        int middle = (crossing.first + crossing.last) / 2;
        int half = Math.min(crossing.width() / 2 + (custom == null ? SLACK : custom.reach()), halfLength(settings.maxWidth));
        BridgeMaterials.Stone stone = style.stone() ? palette.stone : BridgeMaterials.Stone.STONE_BRICKS;
        long seed = random.nextLong();
        boolean broken = random.nextInt(100) < settings.brokenChance && (custom == null || custom.canBreak);
        return new BridgePlan(crossing.alongX, crossing.lateral, middle - half, middle + half, middle, style, palette.wood, stone, palette.moss,
                seed, broken, custom, bank.getRegistryName());
    }

    // A plan across whatever is at the target, for the place command
    static BridgePlan planAt(World world, BlockPos target, boolean alongX, BridgeStyle style, @Nullable CustomBridge custom, boolean broken,
                             Random random) {
        Palette palette = Palette.of(world.getBiome(target));
        int middle = alongX ? target.getX() : target.getZ();
        int half = halfLength(Settings.generalSettings.maxWidth);
        BridgeMaterials.Stone stone = style.stone() ? palette.stone : BridgeMaterials.Stone.STONE_BRICKS;
        return new BridgePlan(alongX, alongX ? target.getZ() : target.getX(), middle - half, middle + half, middle, style, palette.wood, stone,
                palette.moss, random.nextLong(), broken, custom, null);
    }

    @Nullable
    private static Crossing measure(BiomeProvider provider, int x, int z, boolean alongX, int maxWidth) {
        int length = SCAN * 2 + 1;
        Biome[] line = alongX ? provider.getBiomes(null, x - SCAN, z, length, 1, false) : provider.getBiomes(null, x, z - SCAN, 1, length, false);
        if (!isRiver(line[SCAN])) {
            return null;
        }
        int first = SCAN;
        int last = SCAN;
        while (first > 0 && isRiver(line[first - 1])) {
            first--;
        }
        while (last < length - 1 && isRiver(line[last + 1])) {
            last++;
        }
        if (first == 0 || last == length - 1 || last - first + 1 > maxWidth + 4) {
            return null;
        }
        Biome before = line[first - 1];
        Biome after = line[last + 1];
        if (!isBank(before) || !isBank(after)) {
            return null;
        }
        int origin = (alongX ? x : z) - SCAN;
        return new Crossing(alongX, alongX ? z : x, origin + first, origin + last, before, after);
    }

    private static boolean flowsAcross(BiomeProvider provider, int x, int z, Crossing crossing) {
        Biome[] line = crossing.alongX ? provider.getBiomes(null, x, z - 8, 1, 17, false) : provider.getBiomes(null, x - 8, z, 17, 1, false);
        return isRiver(line[0]) && isRiver(line[16]);
    }

    private static boolean isRiver(Biome biome) {
        return biome != null && BiomeDictionary.hasType(biome, BiomeDictionary.Type.RIVER) && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN);
    }

    private static boolean isBank(Biome biome) {
        return biome != null && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN) && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.RIVER)
                && biome.getBaseHeight() <= 0.5F;
    }

    private static final class Crossing {
        final boolean alongX;
        final int lateral;
        final int first;
        final int last;
        final Biome before;
        final Biome after;

        Crossing(boolean alongX, int lateral, int first, int last, Biome before, Biome after) {
            this.alongX = alongX;
            this.lateral = lateral;
            this.first = first;
            this.last = last;
            this.before = before;
            this.after = after;
        }

        int width() {
            return last - first + 1;
        }
    }

    private static final class Palette {
        final BlockPlanks.EnumType wood;
        final BridgeMaterials.Stone stone;
        final float moss;
        // In BridgeStyle order
        final int[] weights;

        Palette(BlockPlanks.EnumType wood, BridgeMaterials.Stone stone, float moss, int... weights) {
            this.wood = wood;
            this.stone = stone;
            this.moss = moss;
            this.weights = weights;
        }

        static Palette of(Biome biome) {
            ResourceLocation name = biome.getRegistryName();
            boolean birch = name != null && name.getPath().contains("birch");
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.MESA)) {
                return new Palette(BlockPlanks.EnumType.OAK, BridgeMaterials.Stone.RED_SANDSTONE, 0.0F, 3, 2, 1, 1, 0, 2, 2, 1);
            }
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.SANDY) && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.BEACH)) {
                return new Palette(BlockPlanks.EnumType.OAK, BridgeMaterials.Stone.SANDSTONE, 0.0F, 3, 2, 1, 1, 0, 2, 2, 1);
            }
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.JUNGLE)) {
                return new Palette(BlockPlanks.EnumType.JUNGLE, BridgeMaterials.Stone.STONE_BRICKS, 0.45F, 1, 0, 2, 1, 3, 0, 1, 1);
            }
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.SAVANNA)) {
                return new Palette(BlockPlanks.EnumType.ACACIA, BridgeMaterials.Stone.STONE_BRICKS, 0.1F, 1, 1, 2, 2, 1, 1, 1, 1);
            }
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.SWAMP)) {
                return new Palette(BlockPlanks.EnumType.OAK, BridgeMaterials.Stone.STONE_BRICKS, 0.4F, 1, 0, 2, 1, 3, 0, 1, 1);
            }
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.SPOOKY)) {
                return new Palette(BlockPlanks.EnumType.DARK_OAK, BridgeMaterials.Stone.STONE_BRICKS, 0.3F, 1, 2, 2, 2, 1, 1, 1, 1);
            }
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.SNOWY)) {
                return new Palette(BlockPlanks.EnumType.SPRUCE, BridgeMaterials.Stone.STONE_BRICKS, 0.0F, 1, 2, 2, 3, 1, 1, 1, 2);
            }
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.CONIFEROUS)) {
                return new Palette(BlockPlanks.EnumType.SPRUCE, BridgeMaterials.Stone.STONE_BRICKS, 0.2F, 1, 2, 2, 3, 1, 1, 1, 2);
            }
            if (birch) {
                return new Palette(BlockPlanks.EnumType.BIRCH, BridgeMaterials.Stone.STONE_BRICKS, 0.15F, 2, 1, 2, 2, 0, 2, 2, 1);
            }
            return new Palette(BlockPlanks.EnumType.OAK, BridgeMaterials.Stone.STONE_BRICKS, 0.25F, 3, 2, 2, 2, 1, 2, 2, 2);
        }

        // The index of a built-in style, or past them the index of a custom bridge plus the number of built-in styles; -1 for none
        int pick(Random random, int river, Biome bank, List<CustomBridge> customs) {
            BridgeStyle[] styles = BridgeStyle.values();
            int[] chances = new int[weights.length + customs.size()];
            for (int i = 0; i < weights.length; i++) {
                chances[i] = usable(styles[i], river, bank) ? weights[i] * styles[i].weight() : 0;
            }
            for (int i = 0; i < customs.size(); i++) {
                CustomBridge custom = customs.get(i);
                chances[weights.length + i] = custom.fits(river) && custom.allowsBiome(bank) ? custom.weight : 0;
            }
            int choice = weighted(random, chances);
            if (choice < 0) {
                // Where the biome gives no weight to any allowed type, the config weights alone decide
                for (int i = 0; i < weights.length; i++) {
                    chances[i] = usable(styles[i], river, bank) ? styles[i].weight() : 0;
                }
                choice = weighted(random, chances);
            }
            return choice;
        }

        private static int weighted(Random random, int[] chances) {
            int total = 0;
            for (int chance : chances) {
                total += chance;
            }
            if (total <= 0) {
                return -1;
            }
            int roll = random.nextInt(total);
            for (int i = 0; i < chances.length; i++) {
                roll -= chances[i];
                if (roll < 0) {
                    return i;
                }
            }
            return -1;
        }

        private static boolean usable(BridgeStyle style, int river, Biome bank) {
            return style.weight() > 0 && style.fits(river) && style.allowsBiome(bank);
        }
    }
}
