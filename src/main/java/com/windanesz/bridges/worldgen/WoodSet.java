package com.windanesz.bridges.worldgen;

import com.windanesz.bridges.DansBridges;
import com.windanesz.bridges.Settings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockNewLog;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockWoodSlab;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// The wooden blocks of a bridge: one of the vanilla woods, the wood of a modded tree worked out from its recipes, or one from the config
public final class WoodSet {

    private static final int SAMPLE_RADIUS = 32;
    // How many columns of the area must have a log of a kind for its wood to be used
    private static final int MIN_TREES = 3;

    // Working a log's wood out goes through every recipe in the pack, so it is done once per log
    private static final Map<IBlockState, Optional<WoodSet>> FROM_LOGS = new ConcurrentHashMap<>();
    private static final Map<IBlockState, WoodSet> BY_PLANKS = new ConcurrentHashMap<>();
    @Nullable
    private static volatile Map<ResourceLocation, WoodSet> overrides;

    private final IBlockState planks;
    private final IBlockState log;
    private final IBlockState slab;
    private final Block stairs;
    private final IBlockState fence;
    // The vanilla wood this is, for the blocks that only vanilla woods have a set of, such as doors and gates
    @Nullable
    final BlockPlanks.EnumType vanilla;

    private WoodSet(IBlockState planks, IBlockState log, IBlockState slab, Block stairs, IBlockState fence, @Nullable BlockPlanks.EnumType vanilla) {
        this.planks = planks;
        this.log = log;
        this.slab = slab;
        this.stairs = stairs;
        this.fence = fence;
        this.vanilla = vanilla;
    }

    public static WoodSet vanilla(BlockPlanks.EnumType wood) {
        return Vanilla.SETS[wood.getMetadata()];
    }

    IBlockState planks() {
        return planks;
    }

    IBlockState log(BlockLog.EnumAxis axis) {
        return orient(log, axis);
    }

    IBlockState slab(boolean top) {
        return half(slab, top);
    }

    Block stairsBlock() {
        return stairs;
    }

    IBlockState stairs(EnumFacing facing) {
        return stairs.getDefaultState().withProperty(BlockStairs.FACING, facing);
    }

    IBlockState fence() {
        return fence;
    }

    // The wood of the bridge at the centre: the biome's wood from the config, or else that of the trees around it, or the fallback
    static WoodSet near(World world, BlockPos center, @Nullable ResourceLocation biome, WoodSet fallback) {
        WoodSet override = override(biome);
        if (override != null) {
            return override;
        }
        if (!Settings.blockSettings.treeWood) {
            return fallback;
        }
        Map<IBlockState, Integer> trees = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - SAMPLE_RADIUS; x <= center.getX() + SAMPLE_RADIUS; x += 2) {
            for (int z = center.getZ() - SAMPLE_RADIUS; z <= center.getZ() + SAMPLE_RADIUS; z += 2) {
                // Only chunks that are there already with their trees, so that looking doesn't generate any
                Chunk chunk = world.getChunkProvider().getLoadedChunk(x >> 4, z >> 4);
                if (chunk == null || !chunk.isTerrainPopulated()) {
                    continue;
                }
                int top = chunk.getHeightValue(x & 15, z & 15);
                for (int y = top; y > top - 24 && y > 0; y--) {
                    pos.setPos(x, y, z);
                    IBlockState state = chunk.getBlockState(pos);
                    if (isTrunk(state, world, pos)) {
                        if (biome == null || biome.equals(chunk.getBiome(pos, world.getBiomeProvider()).getRegistryName())) {
                            trees.merge(withoutAxis(state), 1, Integer::sum);
                        }
                        break;
                    }
                    if (BridgeBuilder.isGround(state)) {
                        break;
                    }
                }
            }
        }
        List<Map.Entry<IBlockState, Integer>> kinds = new ArrayList<>(trees.entrySet());
        kinds.sort((a, b) -> b.getValue() - a.getValue());
        for (Map.Entry<IBlockState, Integer> kind : kinds) {
            if (kind.getValue() < MIN_TREES) {
                break;
            }
            if (!ignored(kind.getKey())) {
                WoodSet wood = fromLog(world, kind.getKey());
                if (wood != null) {
                    return wood;
                }
            }
        }
        return fallback;
    }

    // The set the planks belong to, so that a ramp of them can be climbed by its stairs
    @Nullable
    static WoodSet byPlanks(IBlockState planks) {
        if (planks.getBlock() == Blocks.PLANKS) {
            return vanilla(planks.getValue(BlockPlanks.VARIANT));
        }
        return BY_PLANKS.get(planks);
    }

    // The wood of the trees of the log, or null where the recipes don't give planks, slabs, stairs and fences of it
    @Nullable
    static WoodSet fromLog(World world, IBlockState log) {
        IBlockState key = withoutAxis(log);
        Optional<WoodSet> wood = FROM_LOGS.get(key);
        if (wood == null) {
            wood = Optional.ofNullable(derive(world, key));
            FROM_LOGS.put(key, wood);
            wood.ifPresent(set -> BY_PLANKS.put(set.planks, set));
        }
        return wood.orElse(null);
    }

    static IBlockState withoutAxis(IBlockState log) {
        return orient(log, BlockLog.EnumAxis.Y);
    }

    private static boolean isTrunk(IBlockState state, World world, BlockPos pos) {
        return state.getBlock() instanceof BlockLog || state.getBlock().isWood(world, pos);
    }

    // A log lying along the axis, by vanilla's log axis, or the plain pillar axis of the logs that have no bark-all-round state
    private static IBlockState orient(IBlockState log, BlockLog.EnumAxis axis) {
        if (log.getPropertyKeys().contains(BlockLog.LOG_AXIS)) {
            return log.withProperty(BlockLog.LOG_AXIS, axis);
        }
        if (log.getPropertyKeys().contains(BlockRotatedPillar.AXIS)) {
            return log.withProperty(BlockRotatedPillar.AXIS, axis == BlockLog.EnumAxis.X ? EnumFacing.Axis.X : axis == BlockLog.EnumAxis.Z ? EnumFacing.Axis.Z
                    : EnumFacing.Axis.Y);
        }
        return log;
    }

    private static IBlockState half(IBlockState slab, boolean top) {
        return slab.withProperty(BlockSlab.HALF, top ? BlockSlab.EnumBlockHalf.TOP : BlockSlab.EnumBlockHalf.BOTTOM);
    }

    // Vanilla's half, on a slab block or on one whose single block is also its double slab, as some mods have
    private static boolean isHalfSlab(IBlockState slab) {
        if (!slab.getPropertyKeys().contains(BlockSlab.HALF) || slab.getBlock() instanceof BlockSlab && ((BlockSlab) slab.getBlock()).isDouble()) {
            return false;
        }
        return !half(slab, false).isFullCube();
    }

    @Nullable
    private static WoodSet derive(World world, IBlockState log) {
        if (log.getBlock() == Blocks.LOG) {
            return vanilla(log.getValue(BlockOldLog.VARIANT));
        }
        if (log.getBlock() == Blocks.LOG2) {
            return vanilla(log.getValue(BlockNewLog.VARIANT));
        }
        Item logItem = Item.getItemFromBlock(log.getBlock());
        if (logItem == Items.AIR) {
            return null;
        }
        ItemStack logStack = new ItemStack(logItem, 1, log.getBlock().damageDropped(log));
        IBlockState planks = craft(world, logStack, null, null, null, null, null, null, null, null);
        if (planks == null || !planks.isFullCube() || planks.getMaterial() != Material.WOOD) {
            return null;
        }
        ItemStack p = stackOf(planks);
        ItemStack stick = new ItemStack(Items.STICK);
        IBlockState slab = craft(world, p, p, p, null, null, null, null, null, null);
        IBlockState stairs = craft(world, p, null, null, p, p, null, p, p, p);
        IBlockState fence = craft(world, p, stick, p, p, stick, p, null, null, null);
        if (!(slab != null && isHalfSlab(slab) && stairs != null && stairs.getBlock() instanceof BlockStairs && fence != null
                && fence.getBlock() instanceof BlockFence)) {
            return null;
        }
        return new WoodSet(planks, log, half(slab, false), stairs.getBlock(), fence.getBlock().getDefaultState(), null);
    }

    private static ItemStack stackOf(IBlockState state) {
        return new ItemStack(Item.getItemFromBlock(state.getBlock()), 1, state.getBlock().damageDropped(state));
    }

    // The block a 3 by 3 crafting grid makes, row by row, null for an empty slot
    @SuppressWarnings("deprecation")
    @Nullable
    private static IBlockState craft(World world, @Nullable ItemStack... grid) {
        InventoryCrafting inventory = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer player) {
                return false;
            }
        }, 3, 3);
        for (int i = 0; i < grid.length; i++) {
            if (grid[i] != null) {
                inventory.setInventorySlotContents(i, grid[i].copy());
            }
        }
        ItemStack result = CraftingManager.findMatchingResult(inventory, world);
        Block block = Block.getBlockFromItem(result.getItem());
        return result.isEmpty() || block == Blocks.AIR ? null : block.getStateFromMeta(result.getMetadata());
    }

    @Nullable
    static WoodSet override(@Nullable ResourceLocation biome) {
        if (biome == null) {
            return null;
        }
        Map<ResourceLocation, WoodSet> map = overrides;
        if (map == null) {
            map = parseOverrides(Settings.blockSettings.biomeWoods);
            overrides = map;
        }
        return map.get(biome);
    }

    static boolean ignored(IBlockState log) {
        ResourceLocation name = log.getBlock().getRegistryName();
        for (ResourceLocation ignored : Settings.toResourceLocations(Settings.blockSettings.ignoredLogs)) {
            if (ignored.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static void clearOverrides() {
        overrides = null;
    }

    private static Map<ResourceLocation, WoodSet> parseOverrides(String[] lines) {
        Map<ResourceLocation, WoodSet> map = new HashMap<>();
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            try {
                int equals = line.indexOf('=');
                List<String> blocks = equals < 0 ? Collections.emptyList() : splitTopLevel(line.substring(equals + 1));
                if (blocks.size() != 5) {
                    throw new IllegalArgumentException("it needs a biome, '=' and five blocks: planks, log, slab, stairs and fence");
                }
                ResourceLocation biome = new ResourceLocation(line.substring(0, equals).trim());
                if (!modLoaded(biome) || !blocks.stream().allMatch(block -> modLoaded(new ResourceLocation(blockName(block))))) {
                    // Lines for mods that aren't in the pack do nothing
                    continue;
                }
                IBlockState planks = state(blocks.get(0));
                IBlockState log = state(blocks.get(1));
                IBlockState slab = state(blocks.get(2));
                IBlockState stairs = state(blocks.get(3));
                IBlockState fence = state(blocks.get(4));
                if (!isHalfSlab(slab)) {
                    throw new IllegalArgumentException(blocks.get(2) + " isn't a slab with a top and a bottom half");
                }
                if (!(stairs.getBlock() instanceof BlockStairs)) {
                    throw new IllegalArgumentException(blocks.get(3) + " isn't a stair");
                }
                map.put(biome, new WoodSet(planks, log, half(slab, false), stairs.getBlock(), fence, null));
            } catch (IllegalArgumentException | CommandException e) {
                DansBridges.logger.warn("Skipped the biome wood \"{}\": {}", line, e.getMessage());
            }
        }
        return map;
    }

    private static boolean modLoaded(ResourceLocation name) {
        return name.getNamespace().equals("minecraft") || Loader.isModLoaded(name.getNamespace());
    }

    private static String blockName(String block) {
        int bracket = block.indexOf('[');
        return (bracket < 0 ? block : block.substring(0, bracket)).trim();
    }

    // A block as /setblock takes it: modid:name, with properties in square brackets
    private static IBlockState state(String text) throws CommandException {
        ResourceLocation name = new ResourceLocation(blockName(text));
        Block block = ForgeRegistries.BLOCKS.getValue(name);
        if (block == null || block == Blocks.AIR || !ForgeRegistries.BLOCKS.containsKey(name)) {
            throw new IllegalArgumentException("there is no block " + name);
        }
        int bracket = text.indexOf('[');
        if (bracket < 0) {
            return block.getDefaultState();
        }
        int close = text.lastIndexOf(']');
        if (close < bracket) {
            throw new IllegalArgumentException(text + " has no closing ']'");
        }
        return CommandBase.convertArgToBlockState(block, text.substring(bracket + 1, close));
    }

    // Loaded on first use, after the blocks are registered
    private static final class Vanilla {
        static final WoodSet[] SETS = new WoodSet[BlockPlanks.EnumType.values().length];

        static {
            for (BlockPlanks.EnumType wood : BlockPlanks.EnumType.values()) {
                IBlockState log = wood.getMetadata() < 4 ? Blocks.LOG.getDefaultState().withProperty(BlockOldLog.VARIANT, wood)
                        : Blocks.LOG2.getDefaultState().withProperty(BlockNewLog.VARIANT, wood);
                SETS[wood.getMetadata()] = new WoodSet(Blocks.PLANKS.getDefaultState().withProperty(BlockPlanks.VARIANT, wood), log,
                        Blocks.WOODEN_SLAB.getDefaultState().withProperty(BlockWoodSlab.VARIANT, wood), BridgeMaterials.stairs(wood),
                        BridgeMaterials.fence(wood).getDefaultState(), wood);
            }
        }
    }

    // Splits on the commas that aren't inside the square brackets of a block's properties
    static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(text.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(text.substring(start).trim());
        return parts;
    }
}
