package com.windanesz.bridges.worldgen;

import com.windanesz.bridges.DansBridges;
import com.windanesz.bridges.Settings;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

public class WorldGenBridge implements IWorldGenerator {

    private static final String PROGRESS = DansBridges.MODID;

    private static final Map<World, PlanCache> PLANS = new WeakHashMap<>();

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (!activeIn(world)) {
            return;
        }
        Settings.GeneralSettings settings = Settings.generalSettings;
        int size = settings.spacing * 16;
        // A square's bridge reaches past the square by up to half its length, and the decoration of a chunk reaches 8 blocks past it
        int margin = BridgePlanner.halfLength(settings.maxWidth) + BridgePlan.HALF_WIDTH + 24;
        int minX = Math.floorDiv(chunkX * 16 - margin, size);
        int maxX = Math.floorDiv(chunkX * 16 + 15 + margin, size);
        int minZ = Math.floorDiv(chunkZ * 16 - margin, size);
        int maxZ = Math.floorDiv(chunkZ * 16 + 15 + margin, size);
        for (int squareX = minX; squareX <= maxX; squareX++) {
            for (int squareZ = minZ; squareZ <= maxZ; squareZ++) {
                BridgePlan plan = plan(world, squareX, squareZ);
                if (plan == null) {
                    continue;
                }
                int bit = plan.chunkBit(chunkX, chunkZ);
                if (bit >= 0 && BridgeSiteManager.get(world, PROGRESS).decorate(plan.key(), bit, plan.fullMask())) {
                    build(world, plan, settings);
                }
            }
        }
    }

    private static void build(World world, BridgePlan plan, Settings.GeneralSettings settings) {
        // Every chunk the bridge can reach has to be there, which it is unless the config was changed while the bridge was waiting
        for (int x = plan.minChunkX; x <= plan.minChunkX + plan.chunksX; x++) {
            for (int z = plan.minChunkZ; z <= plan.minChunkZ + plan.chunksZ; z++) {
                if (!world.isChunkGeneratedAt(x, z)) {
                    return;
                }
            }
        }
        int minWidth = Math.min(settings.minWidth, settings.maxWidth);
        int maxWidth = Math.max(settings.minWidth, settings.maxWidth);
        if (BridgeBuilder.build(world, plan, minWidth, maxWidth)) {
            DansBridges.logger.debug("Built a bridge ({}) at {}", plan.style, plan.center(world.getSeaLevel()));
        }
    }

    private static boolean activeIn(World world) {
        Settings.GeneralSettings settings = Settings.generalSettings;
        return settings.enabled && allowsDimension(settings.dimensions, world.provider.getDimension()) && world.getWorldInfo().isMapFeaturesEnabled()
                && world.getWorldType() != WorldType.FLAT;
    }

    // An empty list means every dimension
    public static boolean allowsDimension(int[] dimensions, int dimension) {
        if (dimensions == null || dimensions.length == 0) {
            return true;
        }
        for (int allowed : dimensions) {
            if (allowed == dimension) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static BridgePlan plan(World world, int squareX, int squareZ) {
        long key = (long) squareX << 32 | squareZ & 0xFFFFFFFFL;
        PlanCache cache;
        synchronized (PLANS) {
            cache = PLANS.computeIfAbsent(world, w -> new PlanCache());
        }
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        BridgePlan plan = BridgePlanner.plan(world, squareX, squareZ, Settings.generalSettings);
        cache.put(key, plan);
        return plan;
    }

    // For the place command, which ignores the weights and the biomes, but not the river widths a custom bridge is built over
    public static boolean placeAt(World world, BlockPos target, boolean alongX, BridgeStyle style, @Nullable CustomBridge custom, boolean broken,
                                  Random random) {
        return BridgeBuilder.build(world, BridgePlanner.planAt(world, target, alongX, style, custom, broken, random), 1, 64);
    }

    public static void clearPlans() {
        synchronized (PLANS) {
            PLANS.clear();
        }
    }

    // LRU; a null value marks a square without a bridge
    private static final class PlanCache extends LinkedHashMap<Long, BridgePlan> {
        private static final long serialVersionUID = 1L;

        PlanCache() {
            super(256, 0.75F, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, BridgePlan> eldest) {
            return size() > 4096;
        }
    }
}
