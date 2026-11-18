package com.windanesz.bridges.worldgen;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTException;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.template.PlacementSettings;
import net.minecraft.world.gen.structure.template.Template;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

// Lays a custom bridge's pieces across the water: end, span, pier, span, ... span, end turned around
final class CustomBridgeBuilder {

    private static final IBlockState AIR = Blocks.AIR.getDefaultState();

    private final World world;
    private final BridgePlan plan;
    private final CustomBridge bridge;
    private final BridgeMaterials materials;
    private final Random random;
    // Where the bottom layer of every piece goes: the water surface
    private final int base;
    private final int spans;
    private final int from;
    private final int to;
    // The height of the walkway at each column of the ramps, from the edge of the end or of the deck out to the bank
    @Nullable
    private final int[] ramp;
    private final int near;
    private final int far;
    final int firstColumn;
    final int lastColumn;

    private CustomBridgeBuilder(World world, BridgePlan plan, CustomBridge bridge, BridgeMaterials materials, Random random, int base, int spans, int from,
                                int to, @Nullable int[] ramp, int near, int far, int firstColumn, int lastColumn) {
        this.world = world;
        this.plan = plan;
        this.bridge = bridge;
        this.materials = materials;
        this.random = random;
        this.base = base;
        this.spans = spans;
        this.from = from;
        this.to = to;
        this.ramp = ramp;
        this.near = near;
        this.far = far;
        this.firstColumn = firstColumn;
        this.lastColumn = lastColumn;
    }

    // Null where the bridge doesn't fit within the plan, which is all the chunks that are there when it is built
    @Nullable
    static CustomBridgeBuilder prepare(World world, BridgePlan plan, CustomBridge bridge, BridgeLayout layout, int[] ground, boolean[] water, int[] waterTop,
                                       BridgeMaterials materials, Random random) {
        int river = layout.waterEnd - layout.waterStart + 1;
        int pier = bridge.pier == null ? 0 : bridge.pier.length;
        int spans = CustomBridge.spanCount(river, bridge.span.length, pier);
        int length = spans * bridge.span.length + (spans - 1) * pier;
        int from = layout.waterStart - (length - river) / 2;
        int to = from + length - 1;
        int[] ramp = new int[plan.length()];
        Arrays.fill(ramp, Integer.MIN_VALUE);
        int near;
        int far;
        int first;
        int last;
        // A ramp leads from the outer edge of each end, or else from each end of the deck, on to the bank, so that the bridge doesn't stop
        // at a wall of earth where the bank is higher than the pieces, or in the air where it is lower
        if (bridge.end != null) {
            near = from - bridge.end.length;
            far = to + bridge.end.length;
            if (near < 1 || far >= plan.length() - 1) {
                return null;
            }
            if (bridge.endOuter < 0) {
                return new CustomBridgeBuilder(world, plan, bridge, materials, random, layout.waterTop, spans, from, to, null, near, far, near, far);
            }
            ramp[near] = layout.waterTop + bridge.endOuter;
            ramp[far] = layout.waterTop + bridge.endOuter;
            first = BridgeLayout.approach(ground, water, waterTop, ramp, near, -1);
            last = BridgeLayout.approach(ground, water, waterTop, ramp, far, 1);
        } else {
            // A column further out than the deck itself, so that the first step is a stair at the height of the deck
            if (from < 2 || to >= plan.length() - 2) {
                return null;
            }
            near = from;
            far = to;
            int deck = layout.waterTop + bridge.deck;
            ramp[from] = deck;
            ramp[from - 1] = deck;
            ramp[to] = deck;
            ramp[to + 1] = deck;
            first = BridgeLayout.approach(ground, water, waterTop, ramp, from - 1, -1);
            last = BridgeLayout.approach(ground, water, waterTop, ramp, to + 1, 1);
        }
        if (first < 0 || last < 0) {
            return null;
        }
        return new CustomBridgeBuilder(world, plan, bridge, materials, random, layout.waterTop, spans, from, to, ramp, near, far, first, last);
    }

    int top() {
        return base + bridge.height;
    }

    void place() {
        int column = from;
        for (int i = 0; i < spans; i++) {
            if (i > 0 && bridge.pier != null) {
                place(bridge.pier, column, false, true);
                column += bridge.pier.length;
            }
            place(bridge.span, column, false, false);
            column += bridge.span.length;
        }
        if (bridge.end != null) {
            place(bridge.end, from - bridge.end.length, false, true);
            place(bridge.end, to + bridge.end.length, true, true);
        }
        if (ramp != null) {
            ramp(near, firstColumn, -1);
            ramp(far, lastColumn, 1);
        }
    }

    // The piece's local x 0 goes at the column, its x growing along the line, or against it where the piece is turned around
    private void place(CustomBridge.Piece piece, int column, boolean reversed, boolean extend) {
        Rotation rotation = plan.alongX ? Rotation.NONE : Rotation.CLOCKWISE_90;
        if (reversed) {
            rotation = rotation.add(Rotation.CLOCKWISE_180);
        }
        int half = bridge.width / 2;
        BlockPos origin = plan.pos(column, plan.alongX != reversed ? -half : half, base);
        PlacementSettings settings = new PlacementSettings().setRotation(rotation).setIgnoreEntities(false);
        piece.template.addBlocksToWorld(world, origin, this::process, settings, 2);
        if (extend) {
            for (BlockPos local : piece.bottom) {
                BlockPos at = Template.transformedBlockPos(settings, local).add(origin);
                support(at, world.getBlockState(at));
            }
        }
        for (Map.Entry<BlockPos, String> marker : piece.template.getDataBlocks(origin, settings).entrySet()) {
            marker(marker.getKey(), marker.getValue());
        }
    }

    @Nullable
    private Template.BlockInfo process(World world, BlockPos pos, Template.BlockInfo info) {
        IBlockState existing = world.getBlockState(pos);
        if (existing.getBlockHardness(world, pos) < 0.0F) {
            return null;
        }
        // Air never removes water, so the river stays under an arch
        if (info.blockState.getMaterial() == Material.AIR && existing.getMaterial().isLiquid()) {
            return null;
        }
        if (!bridge.biomeWood) {
            return info;
        }
        IBlockState state = materials.biomeWood(info.blockState);
        return state == info.blockState ? info : new Template.BlockInfo(info.pos, state, info.tileentityData);
    }

    private void support(BlockPos above, IBlockState state) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(above);
        for (int y = above.getY() - 1; y > above.getY() - BridgeBuilder.MAX_DEPTH && y > 0; y--) {
            pos.setY(y);
            if (BridgeBuilder.isGround(world.getBlockState(pos))) {
                return;
            }
            world.setBlockState(pos, state, 2);
        }
    }

    private void marker(BlockPos pos, String marker) {
        if (marker.startsWith(CustomBridge.CHEST)) {
            world.setBlockState(pos, Blocks.CHEST.getDefaultState(), 2);
            TileEntity chest = world.getTileEntity(pos);
            if (chest instanceof TileEntityChest) {
                ((TileEntityChest) chest).setLootTable(new ResourceLocation(marker.substring(CustomBridge.CHEST.length()).trim()), random.nextLong());
            }
        } else if (marker.startsWith(CustomBridge.ENTITY)) {
            world.setBlockState(pos, AIR, 2);
            spawn(pos, marker);
        }
    }

    private void spawn(BlockPos pos, String marker) {
        Entity entity;
        try {
            entity = EntityList.createEntityFromNBT(CustomBridge.entityTag(marker), world);
        } catch (NBTException e) {
            return;
        }
        if (entity == null) {
            return;
        }
        entity.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        if (entity instanceof EntityLiving) {
            EntityLiving living = (EntityLiving) entity;
            // Like the summon command, a mob given no NBT is rolled the way a natural spawn would be
            if (!CustomBridge.hasEntityNbt(marker)) {
                living.onInitialSpawn(world.getDifficultyForLocation(pos), null);
            }
            // Bridges are mostly built far from players, where a hostile mob would be despawned at once
            living.enablePersistence();
        }
        world.spawnEntity(entity);
    }

    // A ramp of the deck's block, as wide as the walkway where it starts, from the column past the reference out to the stop
    private void ramp(int reference, int stop, int direction) {
        int[] levels = ramp;
        if (levels == null) {
            return;
        }
        int half = bridge.width / 2;
        IBlockState block = world.getBlockState(plan.pos(from, 0, base + bridge.deck));
        if (!block.isFullCube()) {
            block = Blocks.COBBLESTONE.getDefaultState();
        }
        EnumFacing towardBridge = direction < 0 ? plan.forward() : plan.forward().getOpposite();
        for (int side = -half; side <= half; side++) {
            if (!walkway(side, direction)) {
                continue;
            }
            for (int column = reference + direction; column != stop + direction; column += direction) {
                int level = levels[column];
                int toward = levels[column - direction];
                int out = column == stop ? level : levels[column + direction];
                EnumFacing facing = null;
                if (out == level - 1 && toward != level - 1) {
                    facing = towardBridge;
                } else if (toward == level - 1 && out != level - 1) {
                    facing = towardBridge.getOpposite();
                }
                IBlockState stairs = facing == null ? null : BridgeMaterials.stairsOf(block, facing);
                BlockPos at = plan.pos(column, side, level);
                world.setBlockState(at, stairs == null ? block : stairs, 2);
                support(at, block);
                for (int y = level + 1; y <= level + 2; y++) {
                    BlockPos above = plan.pos(column, side, y);
                    IBlockState state = world.getBlockState(above);
                    if (state.getMaterial() != Material.AIR && !state.getMaterial().isLiquid() && state.getBlockHardness(world, above) >= 0.0F) {
                        world.setBlockState(above, AIR, 2);
                    }
                }
            }
        }
    }

    // Read from the piece rather than the world, where structure void beside the walkway may have left the bank's earth
    private boolean walkway(int side, int direction) {
        CustomBridge.Piece end = bridge.end;
        boolean reversed = end != null && direction > 0;
        int half = bridge.width / 2;
        int z = plan.alongX != reversed ? side + half : half - side;
        if (end != null) {
            return end.solid(0, bridge.endOuter, z);
        }
        return bridge.span.solid(direction < 0 ? 0 : bridge.span.length - 1, bridge.deck, z);
    }
}
