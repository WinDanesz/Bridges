package com.windanesz.bridges.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.windanesz.bridges.DansBridges;
import com.windanesz.bridges.Settings;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.template.Template;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// A bridge a pack maker built from structure files, loaded from config/dansbridges/bridges/<id>/
public final class CustomBridge {

    static final int MAX_WIDTH = 2 * BridgePlan.HALF_WIDTH + 1;
    static final int MAX_HEIGHT = 32;
    // The lengths keep the whole bridge within the chunks its plan waits for
    static final int MAX_SPAN = 16;
    static final int MAX_PIER = 8;
    static final int MAX_END = 16;
    static final String CHEST = "chest:";
    static final String ENTITY = "entity:";

    private static final String EXAMPLE = "example_toll_bridge";
    private static final String[] EXAMPLE_FILES = {"bridge.json", "span.nbt", "pier.nbt", "end.nbt"};

    @Nullable
    private static File directory;
    private static volatile List<CustomBridge> loaded = Collections.emptyList();

    public final String id;
    final int weight;
    private final ResourceLocation[] biomes;
    final int minRiver;
    final int maxRiver;
    final boolean biomeWood;
    final boolean canBreak;
    final Piece span;
    @Nullable
    final Piece pier;
    @Nullable
    final Piece end;
    // The height of the walkway block above the bottom layer, the same in every piece
    final int deck;
    // The height of the walkway at the outer edge of the end, where a ramp leads on to the bank, or -1 where it has none
    final int endOuter;
    final int width;
    final int height;

    private CustomBridge(File folder) throws InvalidBridgeException {
        id = folder.getName();
        JsonObject json = readJson(new File(folder, "bridge.json"));
        weight = intField(json, "weight", 100);
        biomes = Settings.toResourceLocations(stringsField(json, "biomes"));
        minRiver = intField(json, "min_river", 1);
        maxRiver = intField(json, "max_river", 64);
        biomeWood = booleanField(json, "biome_wood", false);
        canBreak = booleanField(json, "can_break", false);
        if (weight < 0 || minRiver < 1 || minRiver > maxRiver) {
            throw new InvalidBridgeException("bridge.json needs a weight of 0 or more and 1 <= min_river <= max_river");
        }

        Piece span = Piece.read(folder, "span", MAX_SPAN);
        if (span == null) {
            throw new InvalidBridgeException("span.nbt is missing");
        }
        this.span = span;
        pier = Piece.read(folder, "pier", MAX_PIER);
        end = Piece.read(folder, "end", MAX_END);
        width = span.width;
        if (width % 2 == 0 || width > MAX_WIDTH) {
            throw new InvalidBridgeException("span.nbt is " + width + " blocks wide, but a bridge has to be an odd width of at most " + MAX_WIDTH);
        }
        deck = span.walkway(span.length / 2);
        if (deck < 0) {
            throw new InvalidBridgeException("span.nbt has no walkway: no block in the middle of its middle row has two blocks of air above it");
        }
        int height = span.height;
        for (Piece piece : new Piece[]{pier, end}) {
            if (piece == null) {
                continue;
            }
            if (piece.width != width) {
                throw new InvalidBridgeException(piece.name + " is " + piece.width + " blocks wide, but span.nbt is " + width);
            }
            int walkway = piece.walkway(piece == end ? piece.length - 1 : piece.length / 2);
            if (walkway != deck) {
                throw new InvalidBridgeException(piece.name + " has its walkway at y " + walkway + ", but span.nbt has it at y " + deck);
            }
            height = Math.max(height, piece.height);
        }
        this.height = height;
        endOuter = end == null ? -1 : end.walkway(0);
    }

    public static void init(File configDirectory) {
        directory = new File(configDirectory, DansBridges.MODID + "/bridges");
        if (!directory.exists()) {
            copyExample(directory);
        }
    }

    // Called when a server starts, so a pack maker can change the files between worlds
    public static void load() {
        List<CustomBridge> bridges = new ArrayList<>();
        File[] folders = directory == null ? null : directory.listFiles(File::isDirectory);
        if (folders != null) {
            // Sorted, so that the same seed plans the same bridges every time
            Arrays.sort(folders);
            for (File folder : folders) {
                try {
                    bridges.add(new CustomBridge(folder));
                } catch (InvalidBridgeException e) {
                    DansBridges.logger.error("Skipped the custom bridge {}: {}", folder.getName(), e.getMessage());
                }
            }
        }
        loaded = Collections.unmodifiableList(bridges);
        WorldGenBridge.clearPlans();
        DansBridges.logger.info("Loaded {} custom bridge(s)", bridges.size());
    }

    public static List<CustomBridge> all() {
        return loaded;
    }

    @Nullable
    public static CustomBridge get(String id) {
        for (CustomBridge bridge : loaded) {
            if (bridge.id.equals(id)) {
                return bridge;
            }
        }
        return null;
    }

    boolean fits(int river) {
        return river >= minRiver && river <= maxRiver;
    }

    boolean allowsBiome(Biome biome) {
        return biomes.length == 0 || Arrays.asList(biomes).contains(biome.getRegistryName());
    }

    // How far past the middle of the river the bridge can reach: half of a span and a pier it may overhang the water by, the
    // end, the ramp to the bank, and a few columns for the water lying off the middle of the line
    int reach() {
        int overhang = (span.length + (pier == null ? 0 : pier.length) + 1) / 2;
        return overhang + (end == null ? 0 : end.length) + BridgeLayout.MAX_APPROACH + 2 + 4;
    }

    static int spanCount(int river, int span, int pier) {
        int count = 1;
        while (count * span + (count - 1) * pier < river) {
            count++;
        }
        return count;
    }

    static boolean hasEntityNbt(String marker) {
        return marker.indexOf('{') >= 0;
    }

    static NBTTagCompound entityTag(String marker) throws NBTException {
        String rest = marker.substring(ENTITY.length()).trim();
        int brace = rest.indexOf('{');
        NBTTagCompound tag = brace < 0 ? new NBTTagCompound() : JsonToNBT.getTagFromJson(rest.substring(brace));
        tag.setString("id", (brace < 0 ? rest : rest.substring(0, brace)).trim());
        return tag;
    }

    static void checkMarkerFormat(String marker) throws InvalidBridgeException {
        if (marker.startsWith(CHEST)) {
            if (marker.substring(CHEST.length()).trim().isEmpty()) {
                throw new InvalidBridgeException("the Data marker \"" + marker + "\" names no loot table");
            }
        } else if (marker.startsWith(ENTITY)) {
            try {
                if (entityTag(marker).getString("id").isEmpty()) {
                    throw new InvalidBridgeException("the Data marker \"" + marker + "\" names no entity");
                }
            } catch (NBTException e) {
                throw new InvalidBridgeException("the NBT of the Data marker \"" + marker + "\" can't be read: " + e.getMessage());
            }
        } else {
            throw new InvalidBridgeException("the Data marker \"" + marker + "\" is neither chest:<loot table> nor entity:<entity id>");
        }
    }

    private static void copyExample(File directory) {
        for (String file : EXAMPLE_FILES) {
            File target = new File(directory, EXAMPLE + "/" + file);
            try (InputStream in = CustomBridge.class.getResourceAsStream("/assets/" + DansBridges.MODID + "/structures/bridges/" + EXAMPLE + "/" + file)) {
                if (in != null && (target.getParentFile().exists() || target.getParentFile().mkdirs())) {
                    Files.copy(in, target.toPath());
                }
            } catch (IOException e) {
                DansBridges.logger.error("Couldn't copy the example bridge file {}", target, e);
            }
        }
    }

    private static JsonObject readJson(File file) throws InvalidBridgeException {
        if (!file.exists()) {
            throw new InvalidBridgeException("bridge.json is missing");
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        } catch (IOException | JsonParseException | IllegalStateException e) {
            throw new InvalidBridgeException("bridge.json can't be read: " + e.getMessage());
        }
    }

    private static int intField(JsonObject json, String name, int fallback) throws InvalidBridgeException {
        try {
            return json.has(name) ? json.get(name).getAsInt() : fallback;
        } catch (RuntimeException e) {
            throw new InvalidBridgeException("\"" + name + "\" in bridge.json has to be a whole number");
        }
    }

    private static boolean booleanField(JsonObject json, String name, boolean fallback) throws InvalidBridgeException {
        try {
            return json.has(name) ? json.get(name).getAsBoolean() : fallback;
        } catch (RuntimeException e) {
            throw new InvalidBridgeException("\"" + name + "\" in bridge.json has to be true or false");
        }
    }

    private static String[] stringsField(JsonObject json, String name) throws InvalidBridgeException {
        if (!json.has(name)) {
            return new String[0];
        }
        try {
            JsonArray array = json.getAsJsonArray(name);
            String[] strings = new String[array.size()];
            for (int i = 0; i < strings.length; i++) {
                strings[i] = array.get(i).getAsString();
            }
            return strings;
        } catch (RuntimeException e) {
            throw new InvalidBridgeException("\"" + name + "\" in bridge.json has to be a list of names");
        }
    }

    static final class Piece {
        final String name;
        final Template template;
        final int length;
        final int height;
        final int width;
        // The solid blocks of the bottom layer, which are extended down to the ground in piers and ends
        final List<BlockPos> bottom = new ArrayList<>();
        private final Map<BlockPos, IBlockState> states = new HashMap<>();

        private Piece(String name, NBTTagCompound nbt, int maxLength) throws InvalidBridgeException {
            this.name = name;
            NBTTagList size = nbt.getTagList("size", 3);
            length = size.getIntAt(0);
            height = size.getIntAt(1);
            width = size.getIntAt(2);
            if (length < 1 || length > maxLength || height < 1 || height > MAX_HEIGHT) {
                throw new InvalidBridgeException(name + " is " + length + " long and " + height + " high, but it can be at most " + maxLength + " long and "
                        + MAX_HEIGHT + " high");
            }

            // Vanilla would quietly turn a block of a missing mod into air
            NBTTagList palette = nbt.getTagList("palette", 10);
            IBlockState[] paletteStates = new IBlockState[palette.tagCount()];
            for (int i = 0; i < palette.tagCount(); i++) {
                String block = palette.getCompoundTagAt(i).getString("Name");
                if (!ForgeRegistries.BLOCKS.containsKey(new ResourceLocation(block))) {
                    throw new InvalidBridgeException(name + " uses the block " + block + ", which isn't in the game");
                }
                paletteStates[i] = NBTUtil.readBlockState(palette.getCompoundTagAt(i));
            }
            NBTTagList blocks = nbt.getTagList("blocks", 10);
            for (int i = 0; i < blocks.tagCount(); i++) {
                NBTTagCompound block = blocks.getCompoundTagAt(i);
                NBTTagList pos = block.getTagList("pos", 3);
                BlockPos at = new BlockPos(pos.getIntAt(0), pos.getIntAt(1), pos.getIntAt(2));
                int state = block.getInteger("state");
                if (state < 0 || state >= paletteStates.length) {
                    throw new InvalidBridgeException(name + " has a block with no entry in its palette");
                }
                states.put(at, paletteStates[state]);
                if (paletteStates[state].getBlock() == Blocks.STRUCTURE_BLOCK) {
                    NBTTagCompound data = block.getCompoundTag("nbt");
                    if ("DATA".equals(data.getString("mode"))) {
                        checkMarker(data.getString("metadata"));
                    }
                } else if (at.getY() == 0 && paletteStates[state].getMaterial().blocksMovement()) {
                    bottom.add(at);
                }
            }
            NBTTagList entities = nbt.getTagList("entities", 10);
            for (int i = 0; i < entities.tagCount(); i++) {
                String entity = entities.getCompoundTagAt(i).getCompoundTag("nbt").getString("id");
                if (!EntityList.isRegistered(new ResourceLocation(entity))) {
                    throw new InvalidBridgeException(name + " has the entity " + entity + ", which isn't in the game");
                }
            }
            template = new Template();
            template.read(nbt);
        }

        @Nullable
        static Piece read(File folder, String name, int maxLength) throws InvalidBridgeException {
            File file = new File(folder, name + ".nbt");
            if (!file.exists()) {
                return null;
            }
            NBTTagCompound nbt;
            try (InputStream in = new FileInputStream(file)) {
                nbt = CompressedStreamTools.readCompressed(in);
            } catch (IOException e) {
                throw new InvalidBridgeException(file.getName() + " can't be read: " + e.getMessage());
            }
            nbt = FMLCommonHandler.instance().getDataFixer().process(FixTypes.STRUCTURE, nbt);
            return new Piece(file.getName(), nbt, maxLength);
        }

        // The lowest block in the middle row at the column that has two blocks of air above it, or -1
        int walkway(int x) {
            int z = width / 2;
            for (int y = 0; y < height; y++) {
                if (solid(x, y, z) && !solid(x, y + 1, z) && !solid(x, y + 2, z)) {
                    return y;
                }
            }
            return -1;
        }

        boolean solid(int x, int y, int z) {
            IBlockState state = states.get(new BlockPos(x, y, z));
            return state != null && state.getBlock() != Blocks.STRUCTURE_BLOCK && state.getMaterial().blocksMovement();
        }

        private void checkMarker(String marker) throws InvalidBridgeException {
            try {
                checkMarkerFormat(marker);
                if (marker.startsWith(ENTITY)) {
                    String entity = entityTag(marker).getString("id");
                    if (!EntityList.isRegistered(new ResourceLocation(entity))) {
                        throw new InvalidBridgeException("the entity " + entity + " isn't in the game");
                    }
                }
            } catch (InvalidBridgeException | NBTException e) {
                throw new InvalidBridgeException(name + ": " + e.getMessage());
            }
        }
    }

    static final class InvalidBridgeException extends Exception {
        private static final long serialVersionUID = 1L;

        InvalidBridgeException(String message) {
            super(message);
        }
    }
}
