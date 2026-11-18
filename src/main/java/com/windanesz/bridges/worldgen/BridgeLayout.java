package com.windanesz.bridges.worldgen;

import javax.annotation.Nullable;
import java.util.Random;

public final class BridgeLayout {

    static final int SEARCH = 12;
    static final int MAX_STEPS_UP = 4;
    static final int MAX_APPROACH = 12;
    static final int MAX_BANK = 8;
    static final int MIN_GAP = 2;
    static final int MAX_GAP = 6;
    static final int STANDING = 2;

    public final int start;
    public final int end;
    public final int waterStart;
    public final int waterEnd;
    public final int waterTop;
    public final int deckTop;
    private final int[] level;

    private BridgeLayout(int start, int end, int waterStart, int waterEnd, int waterTop, int deckTop, int[] level) {
        this.start = start;
        this.end = end;
        this.waterStart = waterStart;
        this.waterEnd = waterEnd;
        this.waterTop = waterTop;
        this.deckTop = deckTop;
        this.level = level;
    }

    public int level(int column) {
        return level[column];
    }

    public boolean overWater(int column) {
        return column >= waterStart && column <= waterEnd;
    }

    public boolean isEnd(int column) {
        return column == start || column == end;
    }

    // 1 if the walkway rises toward the higher columns, -1 toward the lower ones, 0 where it is flat or a one-block bump
    public int climb(int column) {
        int here = level[column];
        boolean lowerBefore = column > start && level[column - 1] == here - 1;
        boolean lowerAfter = column < end && level[column + 1] == here - 1;
        if (lowerBefore == lowerAfter) {
            return 0;
        }
        return lowerBefore ? 1 : -1;
    }

    public int fromEnd(int column) {
        return Math.min(column - start, end - column);
    }

    // The first and last column of the fallen stretch, or null where the river is too narrow to leave deck standing on both sides
    @Nullable
    public int[] gap(Random random) {
        int span = waterEnd - waterStart + 1;
        int width = Math.max(MIN_GAP, Math.min(MAX_GAP, Math.round(span / 4.0F)));
        int room = span - 2 * STANDING - width;
        if (room < 0) {
            return null;
        }
        // Two rolls averaged, so that it falls in more often in the middle
        int from = waterStart + STANDING + (random.nextInt(room + 1) + random.nextInt(room + 1)) / 2;
        return new int[]{from, from + width - 1};
    }

    @Nullable
    public static BridgeLayout of(int[] ground, boolean[] water, int[] waterTop, int middle, int minWidth, int maxWidth, int clearance, int hump) {
        return of(ground, water, waterTop, middle, minWidth, maxWidth, clearance, hump, false);
    }

    // A fixed deck is exactly the clearance above the water, for custom bridges whose pieces have their walkway at a set height
    @Nullable
    public static BridgeLayout of(int[] ground, boolean[] water, int[] waterTop, int middle, int minWidth, int maxWidth, int clearance, int hump,
                                  boolean fixedDeck) {
        int length = ground.length;
        int found = -1;
        for (int offset = 0; offset <= SEARCH && found < 0; offset++) {
            if (middle - offset >= 0 && middle - offset < length && water[middle - offset]) {
                found = middle - offset;
            } else if (middle + offset >= 0 && middle + offset < length && water[middle + offset]) {
                found = middle + offset;
            }
        }
        if (found < 0) {
            return null;
        }
        int top = waterTop[found];
        // A one block bar of sand at the level of the water is crossed as part of the river
        int first = found;
        while (first > 0 && (water[first - 1] || first > 1 && water[first - 2] && ground[first - 1] <= top)) {
            first -= water[first - 1] ? 1 : 2;
        }
        int last = found;
        while (last < length - 1 && (water[last + 1] || last < length - 2 && water[last + 2] && ground[last + 1] <= top)) {
            last += water[last + 1] ? 1 : 2;
        }
        if (first == 0 || last == length - 1) {
            return null;
        }
        int width = last - first + 1;
        if (width < minWidth || width > maxWidth) {
            return null;
        }
        for (int column = first; column <= last; column++) {
            if (water[column]) {
                top = Math.max(top, waterTop[column]);
            }
        }
        int bankBefore = ground[first - 1];
        int bankAfter = ground[last + 1];
        if (bankBefore > top + MAX_BANK || bankAfter > top + MAX_BANK) {
            return null;
        }
        int deck = fixedDeck ? top + clearance : Math.max(top + clearance, Math.max(Math.min(bankBefore, bankAfter), Math.max(bankBefore, bankAfter) - 2));
        int[] level = new int[length];
        int rise = Math.max(0, Math.min(hump, (width - 1) / 2));
        for (int column = first; column <= last; column++) {
            level[column] = deck + Math.min(rise, Math.min(column - first, last - column));
        }
        int start = approach(ground, water, waterTop, level, first, -1);
        int end = approach(ground, water, waterTop, level, last, 1);
        if (start < 0 || end < 0) {
            return null;
        }
        return new BridgeLayout(start, end, first, last, top, deck, level);
    }

    static int approach(int[] ground, boolean[] water, int[] waterTop, int[] level, int edge, int direction) {
        int previous = level[edge];
        int climbed = 0;
        for (int column = edge + direction, steps = 0; column >= 0 && column < ground.length && steps <= MAX_APPROACH; column += direction, steps++) {
            // Another stretch of water past the bank is bridged at the height of its surface
            int height = water[column] ? waterTop[column] - 1 : ground[column];
            if (height == previous || height == previous - 1 && !water[column]) {
                level[column] = height;
                return column;
            }
            if (height < previous) {
                previous--;
            } else {
                // One step up onto the bank, or one step dug into it where it rises more steeply
                previous++;
                if (++climbed > MAX_STEPS_UP) {
                    return -1;
                }
            }
            level[column] = previous;
        }
        return -1;
    }
}
