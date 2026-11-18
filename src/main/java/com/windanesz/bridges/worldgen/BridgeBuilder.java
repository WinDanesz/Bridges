package com.windanesz.bridges.worldgen;

import com.windanesz.bridges.Settings;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;

final class BridgeBuilder {

    static final int MAX_DEPTH = 40;
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();

    private final World world;
    private final BridgePlan plan;
    private final BridgeLayout layout;
    private final BridgeMaterials materials;
    private final Random random;
    private final EnumFacing.Axis axis;
    // Columns with a beam overhead: the frames of rickety bridges and the gates of railed wooden ones
    @Nullable
    private boolean[] frames;
    @Nullable
    private CustomBridgeBuilder custom;
    // The block a custom bridge's collapse leaves on the river bed, taken from what fell
    @Nullable
    private IBlockState fallen;

    private BridgeBuilder(World world, BridgePlan plan, BridgeLayout layout, Random random) {
        this.world = world;
        this.plan = plan;
        this.layout = layout;
        this.random = random;
        this.materials = new BridgeMaterials(WoodSet.near(world, plan.center(world.getSeaLevel()), plan.bank, WoodSet.vanilla(plan.wood)), plan.stone,
                plan.moss, random);
        this.axis = plan.alongX ? EnumFacing.Axis.X : EnumFacing.Axis.Z;
    }

    static boolean build(World world, BridgePlan plan, int minWidth, int maxWidth) {
        int length = plan.length();
        int[] ground = new int[length];
        boolean[] water = new boolean[length];
        int[] waterTop = new int[length];
        for (int column = 0; column < length; column++) {
            survey(world, plan.pos(column, 0, 0), column, ground, water, waterTop);
        }
        CustomBridge bridge = plan.custom;
        BridgeLayout layout = bridge == null
                ? BridgeLayout.of(ground, water, waterTop, plan.middle - plan.first, minWidth, maxWidth, plan.style.clearance, plan.style.hump)
                : BridgeLayout.of(ground, water, waterTop, plan.middle - plan.first, Math.max(minWidth, bridge.minRiver), Math.min(maxWidth, bridge.maxRiver),
                bridge.deck, 0, true);
        if (layout == null) {
            return false;
        }
        if (!crossesRiver(world, plan, layout)) {
            return false;
        }
        BridgeBuilder builder = new BridgeBuilder(world, plan, layout, new Random(plan.seed));
        if (bridge != null) {
            builder.custom = CustomBridgeBuilder.prepare(world, plan, bridge, layout, ground, water, waterTop, builder.materials, builder.random);
            if (builder.custom == null) {
                return false;
            }
        }
        builder.build();
        return true;
    }

    // Rules out a line that runs along a bank rather than across the river
    private static boolean crossesRiver(World world, BridgePlan plan, BridgeLayout layout) {
        int[] ground = new int[1];
        boolean[] water = new boolean[1];
        int[] top = new int[1];
        int width = layout.waterEnd - layout.waterStart + 1;
        if (width * 2 < layout.end - layout.start + 1) {
            return false;
        }
        int third = Math.max(1, width / 3);
        for (int column = layout.waterStart + third; column <= layout.waterEnd - third; column++) {
            for (int side = -4; side <= 4; side++) {
                survey(world, plan.pos(column, side, 0), 0, ground, water, top);
                if (!water[0]) {
                    return false;
                }
            }
        }
        int wet = 0;
        for (int column = layout.waterStart; column <= layout.waterEnd; column++) {
            boolean all = true;
            for (int side = -2; side <= 2 && all; side += 4) {
                survey(world, plan.pos(column, side, 0), 0, ground, water, top);
                all = water[0];
            }
            wet += all ? 1 : 0;
        }
        return wet * 10 >= width * 7;
    }

    private static void survey(World world, BlockPos column, int index, int[] ground, boolean[] water, int[] waterTop) {
        int top = world.getChunk(column).getTopFilledSegment() + 15;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(column.getX(), top, column.getZ());
        water[index] = false;
        ground[index] = 0;
        for (int y = top; y > 0; y--) {
            pos.setY(y);
            IBlockState state = world.getBlockState(pos);
            Material material = state.getMaterial();
            if (material == Material.WATER || material == Material.ICE || material == Material.PACKED_ICE) {
                if (!water[index]) {
                    water[index] = true;
                    waterTop[index] = y;
                }
            } else if (isGround(state)) {
                ground[index] = y;
                return;
            }
        }
    }

    static boolean isGround(IBlockState state) {
        Material material = state.getMaterial();
        if (material == Material.AIR || material.isLiquid() || material == Material.LEAVES || material == Material.WOOD || material == Material.PLANTS
                || material == Material.VINE || material == Material.GOURD || material == Material.CACTUS || material == Material.WEB || material == Material.SNOW
                || material == Material.CARPET || material == Material.ICE || material == Material.PACKED_ICE || material == Material.CIRCUITS) {
            return false;
        }
        return material.blocksMovement() && !material.isReplaceable();
    }

    private void build() {
        if (custom != null) {
            custom.place();
            finish();
            return;
        }
        clear();
        switch (plan.style) {
            case STONE_ARCH:
                stoneArch();
                break;
            case STONE_RAILED:
                stoneRailed();
                break;
            case WOODEN:
                wooden(false);
                break;
            case WOODEN_RAILED:
                wooden(true);
                break;
            case STONE_HUMPED:
                stoneHumped();
                break;
            case STONE_SMALL:
                stoneSmall();
                break;
            case COBBLESTONE:
                cobblestone();
                break;
            default:
                rickety();
        }
        finish();
    }

    private void finish() {
        if (plan.broken) {
            collapse();
        }
        snow();
    }

    private int firstColumn() {
        return custom != null ? custom.firstColumn : layout.start;
    }

    private int lastColumn() {
        return custom != null ? custom.lastColumn : layout.end;
    }

    // How far to the sides of the line the bridge reaches, buttresses and gates included
    private int reach() {
        return plan.custom != null ? plan.custom.width / 2 : plan.style.halfWidth + 1;
    }

    // The highest block the bridge can have at the column
    private int top(int column) {
        return custom != null ? custom.top() - 1 : layout.level(column) + 5;
    }

    // Vanilla lays its snow before other mods decorate the chunk, so the bridge would be left bare in snowy biomes
    private void snow() {
        IBlockState snow = Blocks.SNOW_LAYER.getDefaultState();
        int reach = reach();
        for (int column = firstColumn(); column <= lastColumn(); column++) {
            for (int side = -reach; side <= reach; side++) {
                for (int y = top(column) + 1; y > layout.waterTop; y--) {
                    if (get(column, side, y).getMaterial() != Material.AIR) {
                        // With the light check, this also asks whether a snow layer can rest on the block below
                        if (world.canSnowAt(plan.pos(column, side, y + 1), true)) {
                            set(column, side, y + 1, snow);
                        }
                        break;
                    }
                }
            }
        }
    }

    // Each half of the width breaks off up to a column apart from the other, so the edges are ragged
    private void collapse() {
        int[] gap = layout.gap(random);
        if (gap == null) {
            return;
        }
        for (int half = -1; half <= 1; half += 2) {
            int from = gap[0] - random.nextInt(2);
            int to = gap[1] + random.nextInt(2);
            int reach = reach();
            int minSide = half < 0 ? -reach : 0;
            int maxSide = half < 0 ? -1 : reach;
            for (int column = from; column <= to; column++) {
                for (int side = minSide; side <= maxSide; side++) {
                    fall(column, side);
                }
            }
        }
        if (frames != null) {
            // A frame the gap reaches falls in whole, instead of leaving half a beam on one post
            for (int column = Math.max(layout.start, gap[0] - 1); column <= Math.min(layout.end, gap[1] + 1); column++) {
                if (frames[column]) {
                    for (int side = -reach(); side <= reach(); side++) {
                        fall(column, side);
                    }
                }
            }
        }
        for (int column = gap[0]; column <= gap[1]; column++) {
            for (int side = -2; side <= 2; side++) {
                if (random.nextFloat() < 0.3F) {
                    rubble(column, side);
                }
            }
        }
    }

    private void fall(int column, int side) {
        for (int y = top(column); y > layout.waterTop; y--) {
            IBlockState state = get(column, side, y);
            if (state.getMaterial() != Material.AIR && !state.getMaterial().isLiquid() && state.getBlockHardness(world, plan.pos(column, side, y)) >= 0.0F) {
                if (state.isFullCube()) {
                    fallen = state;
                }
                set(column, side, y, AIR);
            }
        }
    }

    private IBlockState rubble() {
        if (custom != null) {
            return fallen != null ? fallen : materials.planks();
        }
        return plan.style == BridgeStyle.COBBLESTONE ? materials.cobblestone() : plan.style.stone() ? materials.brick() : materials.planks();
    }

    private void rubble(int column, int side) {
        for (int y = layout.waterTop - 1; y > layout.waterTop - MAX_DEPTH && y > 1; y--) {
            if (isGround(get(column, side, y - 1))) {
                if (get(column, side, y).getMaterial() == Material.WATER) {
                    set(column, side, y, rubble());
                }
                return;
            }
        }
    }

    private void clear() {
        for (int column = layout.start; column <= layout.end; column++) {
            int level = layout.level(column);
            for (int side = -plan.style.halfWidth; side <= plan.style.halfWidth; side++) {
                for (int y = level + 1; y <= level + 4; y++) {
                    IBlockState state = get(column, side, y);
                    if (state.getMaterial() == Material.AIR || state.getMaterial().isLiquid() || state.getBlockHardness(world, plan.pos(column, side, y)) < 0.0F) {
                        continue;
                    }
                    if (Math.abs(side) <= 1 && y <= level + 2 || !isGround(state)) {
                        set(column, side, y, AIR);
                    }
                }
            }
        }
    }

    @Nullable
    private EnumFacing stairFacing(int column) {
        int climb = layout.climb(column);
        return climb == 0 ? null : climb > 0 ? plan.forward() : plan.forward().getOpposite();
    }

    private void support(int column, int side, int fromY, Supplier<IBlockState> block) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = fromY; y > fromY - MAX_DEPTH && y > 0; y--) {
            BlockPos at = plan.pos(column, side, y);
            pos.setPos(at);
            if (isGround(world.getBlockState(pos))) {
                return;
            }
            world.setBlockState(at, block.get(), 2);
        }
    }

    // The top of the arch opening at each column, or Integer.MIN_VALUE where a pier or the bank is
    private int[] arches(int target) {
        int[] open = new int[plan.length()];
        Arrays.fill(open, Integer.MIN_VALUE);
        int height = layout.deckTop - 1 - layout.waterTop;
        if (height < 1) {
            return open;
        }
        int span = layout.waterEnd - layout.waterStart + 1;
        int count = Math.max(1, Math.round(span / (float) target));
        int openWidth = span - (count - 1);
        int column = layout.waterStart;
        for (int arch = 0; arch < count; arch++) {
            int width = openWidth / count + (arch < openWidth % count ? 1 : 0);
            double middle = column + (width - 1) / 2.0D;
            double radius = width / 2.0D + 0.35D;
            for (int at = column; at < column + width; at++) {
                double offset = (at - middle) / radius;
                open[at] = layout.waterTop + (int) Math.round(height * Math.sqrt(Math.max(0.0D, 1.0D - offset * offset)));
            }
            column += width + 1;
        }
        return open;
    }

    private boolean[] posts(int spacing) {
        boolean[] posts = new boolean[plan.length()];
        int first = layout.start + 1;
        int last = layout.end - 1;
        if (last < first) {
            return posts;
        }
        posts[first] = true;
        posts[last] = true;
        for (int column = first + spacing; column <= last - 2; column += spacing) {
            posts[column] = true;
        }
        return posts;
    }

    private void stoneBody(int[] open) {
        stoneBody(open, 2, materials::brick, materials::brick, materials::brick, materials::brick, materials::stoneStairs);
    }

    private void stoneBody(int[] open, int halfWidth, Supplier<IBlockState> core, Supplier<IBlockState> facing, Supplier<IBlockState> walkway,
                           Supplier<IBlockState> pier, Function<EnumFacing, IBlockState> stairs) {
        for (int column = layout.start; column <= layout.end; column++) {
            int level = layout.level(column);
            EnumFacing climb = stairFacing(column);
            for (int side = -halfWidth; side <= halfWidth; side++) {
                boolean edge = Math.abs(side) == halfWidth;
                set(column, side, level, edge ? facing.get() : climb != null ? stairs.apply(climb) : walkway.get());
                if (open[column] == Integer.MIN_VALUE) {
                    support(column, side, level - 1, pier);
                } else {
                    for (int y = level - 1; y > open[column]; y--) {
                        set(column, side, y, edge ? facing.get() : core.get());
                    }
                }
            }
        }
    }

    private void vines() {
        vines(3);
    }

    private void vines(int out) {
        if (!materials.vines()) {
            return;
        }
        for (int column = layout.waterStart; column <= layout.waterEnd; column++) {
            for (int side = -out; side <= out; side += 2 * out) {
                if (random.nextFloat() > 0.3F) {
                    continue;
                }
                EnumFacing clingsTo = side > 0 ? plan.side().getOpposite() : plan.side();
                int length = 1 + random.nextInt(3);
                for (int y = layout.level(column) - 1; y > layout.waterTop && length > 0; y--, length--) {
                    if (get(column, side, y).getMaterial() != Material.AIR || !get(column, side - Integer.signum(side), y).isFullCube()) {
                        break;
                    }
                    set(column, side, y, BridgeMaterials.vine(clingsTo));
                }
            }
        }
    }

    private void stoneArch() {
        int[] open = arches(6);
        stoneBody(open);
        for (int column = layout.start + 1; column < layout.end; column++) {
            EnumFacing facing = stairFacing(column);
            for (int side = -2; side <= 2; side += 4) {
                set(column, side, layout.level(column) + 1, facing != null ? materials.stoneStairs(facing) : materials.brick());
            }
        }
        vines();
    }

    private void stoneRailed() {
        int[] open = arches(12);
        stoneBody(open);
        boolean[] posts = posts(4);
        IBlockState fence = materials.fence();
        for (int column = layout.start + 1; column < layout.end; column++) {
            for (int side = -2; side <= 2; side += 4) {
                set(column, side, layout.level(column) + 1, posts[column] ? materials.chiseled() : fence);
            }
        }
        if (Settings.blockSettings.hangLanterns) {
            lanterns(open);
        }
        vines();
    }

    private void lanterns(int[] open) {
        IBlockState hanging = BridgeMaterials.lantern(true);
        IBlockState standing = BridgeMaterials.lantern(false);
        if (hanging == null) {
            return;
        }
        IBlockState chain = BridgeMaterials.chain();
        int column = layout.waterStart;
        while (column <= layout.waterEnd) {
            if (open[column] == Integer.MIN_VALUE) {
                column++;
                continue;
            }
            int archEnd = column;
            while (archEnd + 1 <= layout.waterEnd && open[archEnd + 1] != Integer.MIN_VALUE) {
                archEnd++;
            }
            int middle = (column + archEnd) / 2;
            int top = open[middle];
            if (get(middle, 0, top).getMaterial() != Material.AIR || get(middle, 0, top - 1).getMaterial() != Material.AIR && top - layout.waterTop >= 3) {
                column = archEnd + 1;
                continue;
            }
            if (top - layout.waterTop >= 3 && chain != null) {
                set(middle, 0, top, chain);
                set(middle, 0, top - 1, hanging);
            } else if (top - layout.waterTop >= 2) {
                set(middle, 0, top, hanging);
            }
            column = archEnd + 1;
        }
        for (int post : new int[]{layout.start + 1, layout.end - 1}) {
            if (post > layout.start && post < layout.end) {
                for (int side = -2; side <= 2; side += 4) {
                    if (get(post, side, layout.level(post) + 2).getMaterial() == Material.AIR) {
                        set(post, side, layout.level(post) + 2, standing);
                    }
                }
            }
        }
    }

    // After 22_0 of YUNG's Bridges
    private void stoneHumped() {
        int[] open = arches(8);
        stoneBody(open, 2, materials::brick, materials::facing, materials::brick, materials::trim, materials::stoneStairs);
        for (int column = layout.start + 1; column < layout.end; column++) {
            EnumFacing facing = stairFacing(column);
            for (int side = -2; side <= 2; side += 4) {
                // Some of the curb has come away
                if (random.nextFloat() < 0.75F) {
                    set(column, side, layout.level(column) + 1, facing != null ? materials.stoneStairs(facing) : materials.stoneSlab());
                }
            }
        }
        vines();
    }

    // After 15_1 of YUNG's Bridges
    private void stoneSmall() {
        int[] open = arches(3);
        stoneBody(open, 2, materials::brick, materials::brick, materials::paving, materials::trim,
                plan.stone == BridgeMaterials.Stone.STONE_BRICKS ? materials::cobblestoneStairs : materials::stoneStairs);
        IBlockState light = Settings.blockSettings.hangLanterns ? BridgeMaterials.lantern(false) : null;
        if (light == null) {
            light = BridgeMaterials.torch();
        }
        for (int column = layout.start + 1; column < layout.end; column++) {
            boolean corner = column == layout.start + 1 || column == layout.end - 1;
            for (int side = -2; side <= 2; side += 4) {
                int level = layout.level(column);
                set(column, side, level + 1, corner ? materials.chiseled() : materials.cobblestoneWall());
                if (corner) {
                    set(column, side, level + 2, light);
                }
            }
        }
        buttresses(open, 3, materials::trim, materials::stoneStairs);
        vines();
    }

    // After 31_0 of YUNG's Bridges
    private void cobblestone() {
        int[] open = arches(7);
        stoneBody(open, 3, materials::cobblestone, materials::cobblestone, materials::cobblestone, materials::cobblestone, materials::cobblestoneStairs);
        for (int column = layout.start + 1; column < layout.end; column++) {
            int along = column - layout.start - 1;
            boolean post = along % 2 == 0 || stairFacing(column) != null || column == layout.end - 1;
            for (int side = -3; side <= 3; side += 6) {
                int level = layout.level(column);
                set(column, side, level + 1, post ? materials.cobblestoneWall() : materials.cobblestoneSlab());
                // Some of the torches have gone out
                if (post && along % 4 == 0 && random.nextFloat() < 0.5F) {
                    set(column, side, level + 2, BridgeMaterials.torch());
                }
            }
        }
        buttresses(open, 4, materials::cobblestone, materials::cobblestoneStairs);
        vines(4);
    }

    private void buttresses(int[] open, int out, Supplier<IBlockState> block, Function<EnumFacing, IBlockState> stairs) {
        for (int column = layout.waterStart; column <= layout.waterEnd; column++) {
            if (open[column] != Integer.MIN_VALUE) {
                continue;
            }
            int level = layout.level(column);
            for (int side = -out; side <= out; side += 2 * out) {
                EnumFacing inward = side > 0 ? plan.side().getOpposite() : plan.side();
                set(column, side, level - 1, stairs.apply(inward));
                support(column, side, level - 2, block);
            }
        }
    }

    private void wooden(boolean railed) {
        boolean[] posts = posts(4);
        IBlockState fence = materials.fence();
        IBlockState lying = materials.log(axis);
        IBlockState standing = materials.log(EnumFacing.Axis.Y);
        for (int column = layout.start; column <= layout.end; column++) {
            int level = layout.level(column);
            EnumFacing facing = stairFacing(column);
            boolean end = layout.isEnd(column);
            for (int side = -2; side <= 2; side++) {
                boolean edge = Math.abs(side) == 2;
                set(column, side, level, facing != null ? materials.woodStairs(facing) : edge ? lying : materials.planks());
                if (!edge || end) {
                    continue;
                }
                if (posts[column]) {
                    support(column, side, level, () -> standing);
                    if (railed || layout.fromEnd(column) == 1) {
                        set(column, side, level + 1, standing);
                    }
                } else {
                    if (!layout.overWater(column)) {
                        support(column, side, level - 1, () -> standing);
                    }
                    if (railed) {
                        set(column, side, level + 1, fence);
                    }
                }
            }
        }
        if (railed) {
            gates();
        }
    }

    private void gates() {
        List<Integer> gates = new ArrayList<>();
        gates.add(flatNear(layout.waterStart - 1, -1));
        gates.add(flatNear(layout.waterEnd + 1, 1));
        if (layout.waterEnd - layout.waterStart + 1 >= 20) {
            gates.add((layout.waterStart + layout.waterEnd) / 2);
        }
        for (int column : gates) {
            if (column <= layout.start || column >= layout.end || stairFacing(column) != null) {
                continue;
            }
            if (frames == null) {
                frames = new boolean[plan.length()];
            }
            frames[column] = true;
            int level = layout.level(column);
            for (int side = -2; side <= 2; side += 4) {
                support(column, side, level - 1, materials::cobblestone);
                for (int y = level + 1; y <= level + 3; y++) {
                    set(column, side, y, materials.cobblestone());
                }
            }
            for (int side = -3; side <= 3; side++) {
                set(column, side, level + 4, materials.cobblestone());
                set(column, side, level + 5, materials.slab(false));
            }
        }
    }

    // Looks outward along the bank first, then a few columns inward over the water
    private int flatNear(int bank, int outward) {
        for (int column = bank; column > layout.start && column < layout.end; column += outward) {
            if (stairFacing(column) == null) {
                return column;
            }
        }
        for (int column = bank - outward, steps = 0; steps < 4 && layout.overWater(column); column -= outward, steps++) {
            if (stairFacing(column) == null) {
                return column;
            }
        }
        return -1;
    }

    private void rickety() {
        IBlockState fence = materials.fence();
        IBlockState topSlab = materials.slab(true);
        // A bottom slab, so that the beam rests on the posts instead of floating half a block above them
        IBlockState beam = materials.slab(false);
        frames = new boolean[plan.length()];
        int lastFrame = Integer.MIN_VALUE / 2;
        for (int column = layout.start; column <= layout.end; column++) {
            int level = layout.level(column);
            EnumFacing facing = stairFacing(column);
            boolean end = layout.isEnd(column);
            for (int side = -1; side <= 1; side++) {
                // Worn boards are missing or loose only at the sides, flush with the planks, so the middle stays walkable
                float roll = random.nextFloat();
                boolean worn = side != 0 && !end && facing == null;
                if (worn && roll < 0.15F) {
                    continue;
                }
                IBlockState board = facing != null ? materials.woodStairs(facing) : worn && roll > 0.78F ? topSlab : materials.planks();
                set(column, side, level, board);
            }
            if (end) {
                continue;
            }
            // Frames only where the walkway is level, so the beams don't step up and down with the ramps
            boolean flat = layout.level(column - 1) == level && layout.level(column + 1) == level;
            boolean frame = flat && column - lastFrame >= 6 && column < layout.end - 1;
            if (frame) {
                lastFrame = column;
                frames[column] = true;
                for (int side = -2; side <= 2; side += 4) {
                    support(column, side, level + 2, () -> fence);
                }
                for (int side = -2; side <= 2; side++) {
                    set(column, side, level + 3, beam);
                }
            } else if ((column - layout.start) % 2 == 1) {
                // Not beside a frame post, which a stilt would join into a ladder
                for (int side = -1; side <= 1; side += 2) {
                    support(column, side, level - 1, () -> fence);
                }
            }
        }
        for (int column : new int[]{layout.waterStart - 1, layout.waterEnd + 1}) {
            if (column < layout.start || column > layout.end) {
                continue;
            }
            for (int side = -2; side <= 2; side += 4) {
                support(column, side, layout.level(column) + 1, materials::cobblestone);
            }
        }
    }

    private IBlockState get(int column, int side, int y) {
        return world.getBlockState(plan.pos(column, side, y));
    }

    private void set(int column, int side, int y, IBlockState state) {
        world.setBlockState(plan.pos(column, side, y), state, 2);
    }
}
