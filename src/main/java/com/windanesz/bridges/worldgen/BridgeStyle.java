package com.windanesz.bridges.worldgen;

import com.windanesz.bridges.Settings;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;

import javax.annotation.Nullable;
import java.util.Arrays;

// After the bridges of YUNG's Bridges
public enum BridgeStyle {

    STONE_ARCH(3, 2, 2, 0, Integer.MAX_VALUE),
    STONE_RAILED(4, 0, 2, 0, Integer.MAX_VALUE),
    WOODEN(1, 0, 2, 0, Integer.MAX_VALUE),
    WOODEN_RAILED(2, 2, 2, 0, Integer.MAX_VALUE),
    RICKETY(2, 1, 2, 0, Integer.MAX_VALUE),
    STONE_HUMPED(3, 3, 2, 0, Integer.MAX_VALUE),
    STONE_SMALL(2, 1, 2, 0, 14),
    COBBLESTONE(3, 3, 3, 12, Integer.MAX_VALUE),
    // A pack maker's bridge, whose shape comes from its CustomBridge. Last, so that the biome weights line up with the others
    CUSTOM(0, 0, BridgePlan.HALF_WIDTH, 0, Integer.MAX_VALUE);

    public final int clearance;
    public final int hump;
    public final int halfWidth;
    public final int minRiver;
    public final int maxRiver;

    BridgeStyle(int clearance, int hump, int halfWidth, int minRiver, int maxRiver) {
        this.clearance = clearance;
        this.hump = hump;
        this.halfWidth = halfWidth;
        this.minRiver = minRiver;
        this.maxRiver = maxRiver;
    }

    public Settings.TypeSettings settings() {
        Settings.BridgeTypeSettings settings = Settings.bridgeTypeSettings;
        switch (this) {
            case STONE_ARCH:
                return settings.stoneArchBridges;
            case STONE_RAILED:
                return settings.stoneRailedBridges;
            case WOODEN:
                return settings.woodenBridges;
            case WOODEN_RAILED:
                return settings.woodenRailedBridges;
            case STONE_HUMPED:
                return settings.stoneHumpedBridges;
            case STONE_SMALL:
                return settings.stoneSmallBridges;
            case COBBLESTONE:
                return settings.cobblestoneBridges;
            case RICKETY:
                return settings.ricketyBridges;
            default:
                throw new IllegalStateException("Custom bridges have their own settings");
        }
    }

    public int weight() {
        return settings().weight;
    }

    public boolean allowsBiome(Biome biome) {
        return allows(settings().biomes, biome.getRegistryName());
    }

    // An empty whitelist allows every biome
    static boolean allows(String[] whitelist, @Nullable ResourceLocation biome) {
        ResourceLocation[] allowed = whitelist == null ? new ResourceLocation[0] : Settings.toResourceLocations(whitelist);
        return allowed.length == 0 || biome != null && Arrays.asList(allowed).contains(biome);
    }

    // River width as the biomes measure it, not the water in the world
    public boolean fits(int river) {
        return river >= minRiver && river <= maxRiver;
    }

    public boolean stone() {
        return this == STONE_ARCH || this == STONE_RAILED || this == STONE_HUMPED || this == STONE_SMALL;
    }
}
