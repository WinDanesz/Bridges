package com.windanesz.bridges.worldgen;

import net.minecraft.block.BlockPlanks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;

public final class BridgePlan {

    static final int HALF_WIDTH = 5;

    public final boolean alongX;
    public final int lateral;
    public final int first;
    public final int last;
    public final int middle;
    public final BridgeStyle style;
    public final BlockPlanks.EnumType wood;
    public final BridgeMaterials.Stone stone;
    public final float moss;
    public final long seed;
    public final boolean broken;
    @Nullable
    public final CustomBridge custom;
    // The biome of the bank the bridge takes its look from, whose trees give it its wood
    @Nullable
    public final ResourceLocation bank;

    final int minChunkX;
    final int minChunkZ;
    final int chunksX;
    final int chunksZ;

    public BridgePlan(boolean alongX, int lateral, int first, int last, int middle, BridgeStyle style, BlockPlanks.EnumType wood, BridgeMaterials.Stone stone,
                      float moss, long seed, boolean broken, @Nullable CustomBridge custom, @Nullable ResourceLocation bank) {
        this.alongX = alongX;
        this.lateral = lateral;
        this.first = first;
        this.last = last;
        this.middle = middle;
        this.style = style;
        this.wood = wood;
        this.stone = stone;
        this.moss = moss;
        this.seed = seed;
        this.broken = broken;
        this.custom = custom;
        this.bank = bank;
        int minX = alongX ? first : lateral - HALF_WIDTH;
        int maxX = alongX ? last : lateral + HALF_WIDTH;
        int minZ = alongX ? lateral - HALF_WIDTH : first;
        int maxZ = alongX ? lateral + HALF_WIDTH : last;
        this.minChunkX = (minX - 8) >> 4;
        this.minChunkZ = (minZ - 8) >> 4;
        this.chunksX = ((maxX - 8) >> 4) - minChunkX + 1;
        this.chunksZ = ((maxZ - 8) >> 4) - minChunkZ + 1;
    }

    public String key() {
        return (alongX ? "x" : "z") + ',' + lateral + ',' + first + ',' + last;
    }

    public int chunkBit(int chunkX, int chunkZ) {
        int dx = chunkX - minChunkX;
        int dz = chunkZ - minChunkZ;
        if (dx < 0 || dz < 0 || dx >= chunksX || dz >= chunksZ) {
            return -1;
        }
        return dz * chunksX + dx;
    }

    public long fullMask() {
        int count = chunksX * chunksZ;
        return count >= 64 ? -1L : (1L << count) - 1L;
    }

    // column counts from first, side is the offset across the line
    public BlockPos pos(int column, int side, int y) {
        return alongX ? new BlockPos(first + column, y, lateral + side) : new BlockPos(lateral + side, y, first + column);
    }

    public int length() {
        return last - first + 1;
    }

    public EnumFacing forward() {
        return alongX ? EnumFacing.EAST : EnumFacing.SOUTH;
    }

    public EnumFacing side() {
        return alongX ? EnumFacing.SOUTH : EnumFacing.EAST;
    }

    public BlockPos center(int seaLevel) {
        return alongX ? new BlockPos(middle, seaLevel, lateral) : new BlockPos(lateral, seaLevel, middle);
    }
}
