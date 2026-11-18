package com.windanesz.bridges.worldgen;

import com.windanesz.bridges.Settings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockNewLog;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockRedSandstone;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.BlockSandStone;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockStone;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.BlockStoneSlab;
import net.minecraft.block.BlockStoneSlabNew;
import net.minecraft.block.BlockVine;
import net.minecraft.block.BlockWall;
import net.minecraft.block.BlockWoodSlab;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Random;

final class BridgeMaterials {

    enum Stone {
        STONE_BRICKS, SANDSTONE, RED_SANDSTONE
    }

    private final WoodSet wood;
    private final Stone stone;
    private final float moss;
    private final Random random;

    BridgeMaterials(WoodSet wood, Stone stone, float moss, Random random) {
        this.wood = wood;
        this.stone = stone;
        this.moss = moss;
        this.random = random;
    }

    IBlockState planks() {
        return wood.planks();
    }

    IBlockState slab(boolean top) {
        return wood.slab(top);
    }

    IBlockState woodStairs(EnumFacing facing) {
        return wood.stairs(facing);
    }

    IBlockState fence() {
        return wood.fence();
    }

    IBlockState log(EnumFacing.Axis axis) {
        return wood.log(BlockLog.EnumAxis.fromFacingAxis(axis));
    }

    static Block stairs(BlockPlanks.EnumType wood) {
        return byWood(wood, Blocks.OAK_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.BIRCH_STAIRS, Blocks.JUNGLE_STAIRS, Blocks.ACACIA_STAIRS, Blocks.DARK_OAK_STAIRS);
    }

    static Block fence(BlockPlanks.EnumType wood) {
        return byWood(wood, Blocks.OAK_FENCE, Blocks.SPRUCE_FENCE, Blocks.BIRCH_FENCE, Blocks.JUNGLE_FENCE, Blocks.ACACIA_FENCE, Blocks.DARK_OAK_FENCE);
    }

    // The blocks are in the order of BlockPlanks.EnumType
    private static Block byWood(BlockPlanks.EnumType wood, Block... blocks) {
        return blocks[wood.getMetadata()];
    }

    // Vanilla oak in a custom bridge's pieces becomes the wood of the bridge, keeping its shape and facing
    IBlockState biomeWood(IBlockState state) {
        if (wood.vanilla == BlockPlanks.EnumType.OAK) {
            return state;
        }
        Block block = state.getBlock();
        if (block == Blocks.PLANKS) {
            return state.getValue(BlockPlanks.VARIANT) == BlockPlanks.EnumType.OAK ? planks() : state;
        }
        if (block == Blocks.WOODEN_SLAB) {
            return state.getValue(BlockWoodSlab.VARIANT) == BlockPlanks.EnumType.OAK ? slab(state.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.TOP) : state;
        }
        if (block == Blocks.DOUBLE_WOODEN_SLAB) {
            // Modded woods have no double slab here, and planks look the same
            return state.getValue(BlockWoodSlab.VARIANT) == BlockPlanks.EnumType.OAK ? planks() : state;
        }
        if (block == Blocks.LOG) {
            return state.getValue(BlockOldLog.VARIANT) == BlockPlanks.EnumType.OAK ? wood.log(state.getValue(BlockLog.LOG_AXIS)) : state;
        }
        if (block == Blocks.OAK_STAIRS) {
            return copyProperties(state, wood.stairsBlock());
        }
        if (block == Blocks.OAK_FENCE) {
            return copyProperties(state, fence().getBlock());
        }
        // Gates and doors only come in the vanilla woods
        if (wood.vanilla == null) {
            return state;
        }
        if (block == Blocks.OAK_FENCE_GATE) {
            return copyProperties(state, byWood(wood.vanilla, Blocks.OAK_FENCE_GATE, Blocks.SPRUCE_FENCE_GATE, Blocks.BIRCH_FENCE_GATE,
                    Blocks.JUNGLE_FENCE_GATE, Blocks.ACACIA_FENCE_GATE, Blocks.DARK_OAK_FENCE_GATE));
        }
        if (block == Blocks.OAK_DOOR) {
            return copyProperties(state, byWood(wood.vanilla, Blocks.OAK_DOOR, Blocks.SPRUCE_DOOR, Blocks.BIRCH_DOOR, Blocks.JUNGLE_DOOR,
                    Blocks.ACACIA_DOOR, Blocks.DARK_OAK_DOOR));
        }
        return state;
    }

    private static IBlockState copyProperties(IBlockState from, Block to) {
        IBlockState state = to.getDefaultState();
        for (IProperty<?> property : from.getPropertyKeys()) {
            if (state.getPropertyKeys().contains(property)) {
                state = copyProperty(from, state, property);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> IBlockState copyProperty(IBlockState from, IBlockState to, IProperty<T> property) {
        return to.withProperty(property, from.getValue(property));
    }

    // The stairs to climb a ramp of the block with, or null where vanilla has none
    @Nullable
    static IBlockState stairsOf(IBlockState block, EnumFacing facing) {
        Block stairs = null;
        Block base = block.getBlock();
        WoodSet wood = WoodSet.byPlanks(block);
        if (wood != null) {
            stairs = wood.stairsBlock();
        } else if (base == Blocks.STONEBRICK) {
            stairs = Blocks.STONE_BRICK_STAIRS;
        } else if (base == Blocks.COBBLESTONE || base == Blocks.MOSSY_COBBLESTONE) {
            stairs = Blocks.STONE_STAIRS;
        } else if (base == Blocks.BRICK_BLOCK) {
            stairs = Blocks.BRICK_STAIRS;
        } else if (base == Blocks.SANDSTONE) {
            stairs = Blocks.SANDSTONE_STAIRS;
        } else if (base == Blocks.RED_SANDSTONE) {
            stairs = Blocks.RED_SANDSTONE_STAIRS;
        } else if (base == Blocks.NETHER_BRICK) {
            stairs = Blocks.NETHER_BRICK_STAIRS;
        } else if (base == Blocks.QUARTZ_BLOCK) {
            stairs = Blocks.QUARTZ_STAIRS;
        } else if (base == Blocks.PURPUR_BLOCK) {
            stairs = Blocks.PURPUR_STAIRS;
        }
        return stairs == null ? null : stairs.getDefaultState().withProperty(BlockStairs.FACING, facing);
    }

    IBlockState brick() {
        switch (stone) {
            case SANDSTONE:
                return Blocks.SANDSTONE.getDefaultState().withProperty(BlockSandStone.TYPE,
                        random.nextFloat() < 0.2F ? BlockSandStone.EnumType.DEFAULT : BlockSandStone.EnumType.SMOOTH);
            case RED_SANDSTONE:
                return Blocks.RED_SANDSTONE.getDefaultState().withProperty(BlockRedSandstone.TYPE,
                        random.nextFloat() < 0.2F ? BlockRedSandstone.EnumType.DEFAULT : BlockRedSandstone.EnumType.SMOOTH);
            default:
                float roll = random.nextFloat();
                BlockStoneBrick.EnumType type = roll < moss ? BlockStoneBrick.EnumType.MOSSY
                        : roll < moss + 0.12F ? BlockStoneBrick.EnumType.CRACKED : BlockStoneBrick.EnumType.DEFAULT;
                return Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, type);
        }
    }

    IBlockState chiseled() {
        switch (stone) {
            case SANDSTONE:
                return Blocks.SANDSTONE.getDefaultState().withProperty(BlockSandStone.TYPE, BlockSandStone.EnumType.CHISELED);
            case RED_SANDSTONE:
                return Blocks.RED_SANDSTONE.getDefaultState().withProperty(BlockRedSandstone.TYPE, BlockRedSandstone.EnumType.CHISELED);
            default:
                return Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CHISELED);
        }
    }

    IBlockState stoneStairs(EnumFacing facing) {
        Block stairs;
        switch (stone) {
            case SANDSTONE:
                stairs = Blocks.SANDSTONE_STAIRS;
                break;
            case RED_SANDSTONE:
                stairs = Blocks.RED_SANDSTONE_STAIRS;
                break;
            default:
                stairs = Blocks.STONE_BRICK_STAIRS;
                Block mossy = firstBlock(Settings.blockSettings.mossyStoneBrickStairs);
                if (mossy instanceof BlockStairs && random.nextFloat() < moss) {
                    stairs = mossy;
                }
        }
        return stairs.getDefaultState().withProperty(BlockStairs.FACING, facing);
    }

    IBlockState cobblestone() {
        return (random.nextFloat() < Math.max(moss, 0.15F) ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).getDefaultState();
    }

    IBlockState cobblestoneStairs(EnumFacing facing) {
        return Blocks.STONE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, facing);
    }

    IBlockState cobblestoneSlab() {
        return Blocks.STONE_SLAB.getDefaultState().withProperty(BlockStoneSlab.VARIANT, BlockStoneSlab.EnumType.COBBLESTONE)
                .withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.BOTTOM);
    }

    IBlockState cobblestoneWall() {
        return Blocks.COBBLESTONE_WALL.getDefaultState().withProperty(BlockWall.VARIANT,
                random.nextFloat() < Math.max(moss, 0.15F) ? BlockWall.EnumType.MOSSY : BlockWall.EnumType.NORMAL);
    }

    IBlockState stoneSlab() {
        switch (stone) {
            case SANDSTONE:
                return Blocks.STONE_SLAB.getDefaultState().withProperty(BlockStoneSlab.VARIANT, BlockStoneSlab.EnumType.SAND)
                        .withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.BOTTOM);
            case RED_SANDSTONE:
                return Blocks.STONE_SLAB2.getDefaultState().withProperty(BlockStoneSlabNew.VARIANT, BlockStoneSlabNew.EnumType.RED_SANDSTONE)
                        .withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.BOTTOM);
            default:
                return Blocks.STONE_SLAB.getDefaultState().withProperty(BlockStoneSlab.VARIANT, BlockStoneSlab.EnumType.SMOOTHBRICK)
                        .withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.BOTTOM);
        }
    }

    IBlockState trim() {
        switch (stone) {
            case SANDSTONE:
                return Blocks.SANDSTONE.getDefaultState().withProperty(BlockSandStone.TYPE, BlockSandStone.EnumType.SMOOTH);
            case RED_SANDSTONE:
                return Blocks.RED_SANDSTONE.getDefaultState().withProperty(BlockRedSandstone.TYPE, BlockRedSandstone.EnumType.SMOOTH);
            default:
                return Blocks.STONE.getDefaultState().withProperty(BlockStone.VARIANT, BlockStone.EnumType.ANDESITE_SMOOTH);
        }
    }

    IBlockState facing() {
        if (stone != Stone.STONE_BRICKS) {
            return random.nextFloat() < 0.6F ? trim() : brick();
        }
        float roll = random.nextFloat();
        return roll < 0.55F ? trim() : roll < 0.8F ? cobblestone() : brick();
    }

    IBlockState paving() {
        if (stone != Stone.STONE_BRICKS) {
            return brick();
        }
        float roll = random.nextFloat();
        return roll < 0.45F ? cobblestone() : Blocks.STONE.getDefaultState().withProperty(BlockStone.VARIANT,
                roll < 0.75F ? BlockStone.EnumType.ANDESITE : BlockStone.EnumType.ANDESITE_SMOOTH);
    }

    static IBlockState torch() {
        return Blocks.TORCH.getDefaultState();
    }

    boolean vines() {
        return stone == Stone.STONE_BRICKS && moss > 0.0F;
    }

    static IBlockState vine(EnumFacing clingsTo) {
        return Blocks.VINE.getDefaultState().withProperty(BlockVine.getPropertyFor(clingsTo), true);
    }

    @Nullable
    static IBlockState lantern(boolean hanging) {
        // The lanterns of other mods hang by a property named "hanging"
        Block lantern = firstBlock(Settings.blockSettings.lanterns);
        if (lantern == null) {
            return null;
        }
        IBlockState state = lantern.getDefaultState();
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (property instanceof PropertyBool && property.getName().equals("hanging")) {
                return state.withProperty((PropertyBool) property, hanging);
            }
        }
        return null;
    }

    @Nullable
    static IBlockState chain() {
        Block chain = firstBlock(Settings.blockSettings.chains);
        if (chain == null) {
            return null;
        }
        // The chain does not stand up by default: its first axis is x, which lies on its side
        IBlockState state = chain.getDefaultState();
        return state.getPropertyKeys().contains(BlockRotatedPillar.AXIS) ? state.withProperty(BlockRotatedPillar.AXIS, EnumFacing.Axis.Y) : state;
    }

    @Nullable
    static Block firstBlock(String[] names) {
        if (names == null) {
            return null;
        }
        for (ResourceLocation id : Settings.toResourceLocations(names)) {
            if (ForgeRegistries.BLOCKS.containsKey(id)) {
                Block block = ForgeRegistries.BLOCKS.getValue(id);
                if (block != null && block != Blocks.AIR) {
                    return block;
                }
            }
        }
        return null;
    }
}
