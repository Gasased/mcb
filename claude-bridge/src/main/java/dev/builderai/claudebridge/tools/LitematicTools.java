package dev.builderai.claudebridge.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.builderai.claudebridge.ClientThread;
import dev.builderai.claudebridge.litematic.LitematicWriter;
import dev.builderai.claudebridge.mcp.McpTool;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LitematicTools {
    private LitematicTools() {}

    public static List<McpTool> all() {
        return List.of(createLitematic(), placeLitematic());
    }

    static Path schematicsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("schematics");
    }

    static McpTool createLitematic() {
        return new SimpleTool("create_litematic",
                "Create a .litematic schematic file from a list of blocks and save it into the Litematica schematics folder. "
                        + "Coordinates are RELATIVE to the schematic's min corner, all >= 0. "
                        + "Each block: {\"pos\":[x,y,z],\"block\":\"minecraft:stone\"} with optional \"props\":{\"facing\":\"north\"}. "
                        + "For repetitive geometry prefer 'fills': {\"from\":[x,y,z],\"to\":[x,y,z],\"block\":\"...\",\"props\":{...},\"hollow\":false} — "
                        + "fills are applied first (in order), then individual blocks override. "
                        + "Returns the saved file path; follow up with place_litematic.",
                McpTool.schema("{"
                        + "\"name\":{\"type\":\"string\",\"description\":\"schematic name (file name)\"},"
                        + "\"blocks\":{\"type\":\"array\",\"description\":\"individual blocks\"},"
                        + "\"fills\":{\"type\":\"array\",\"description\":\"cuboid fills, applied before blocks\"}"
                        + "}", "name"),
                args -> {
                    String name = args.get("name").getAsString().replaceAll("[^a-zA-Z0-9_\\- ]", "_");
                    Map<Long, LitematicWriter.Entry> blocks = new LinkedHashMap<>();

                    if (args.has("fills")) {
                        for (JsonElement el : args.getAsJsonArray("fills")) {
                            JsonObject fill = el.getAsJsonObject();
                            int[] from = xyz(fill.getAsJsonArray("from"));
                            int[] to = xyz(fill.getAsJsonArray("to"));
                            String block = fill.get("block").getAsString();
                            Map<String, String> props = props(fill);
                            boolean hollow = fill.has("hollow") && fill.get("hollow").getAsBoolean();
                            for (int x = Math.min(from[0], to[0]); x <= Math.max(from[0], to[0]); x++)
                                for (int y = Math.min(from[1], to[1]); y <= Math.max(from[1], to[1]); y++)
                                    for (int z = Math.min(from[2], to[2]); z <= Math.max(from[2], to[2]); z++) {
                                        if (hollow && x != Math.min(from[0], to[0]) && x != Math.max(from[0], to[0])
                                                && y != Math.min(from[1], to[1]) && y != Math.max(from[1], to[1])
                                                && z != Math.min(from[2], to[2]) && z != Math.max(from[2], to[2]))
                                            continue;
                                        put(blocks, x, y, z, block, props);
                                    }
                        }
                    }
                    if (args.has("blocks")) {
                        for (JsonElement el : args.getAsJsonArray("blocks")) {
                            JsonObject b = el.getAsJsonObject();
                            int[] p = xyz(b.getAsJsonArray("pos"));
                            put(blocks, p[0], p[1], p[2], b.get("block").getAsString(), props(b));
                        }
                    }
                    if (blocks.isEmpty()) throw new IllegalArgumentException("Provide 'blocks' and/or 'fills'");
                    if (blocks.size() > 2_000_000) throw new IllegalArgumentException("Too many blocks: " + blocks.size());

                    int sx = 0, sy = 0, sz = 0;
                    List<LitematicWriter.Entry> entries = new ArrayList<>(blocks.values());
                    for (LitematicWriter.Entry e : entries) {
                        sx = Math.max(sx, e.x() + 1);
                        sy = Math.max(sy, e.y() + 1);
                        sz = Math.max(sz, e.z() + 1);
                    }
                    Path file = schematicsDir().resolve(name + ".litematic");
                    LitematicWriter.write(file, name, "Claude", entries, sx, sy, sz);
                    return "Saved " + file + " (size " + sx + "x" + sy + "x" + sz + ", "
                            + entries.size() + " blocks). Use place_litematic to load it into the world.";
                });
    }

    static McpTool placeLitematic() {
        return new SimpleTool("place_litematic",
                "Load a .litematic schematic into Litematica and create a placement at world origin [x,y,z] "
                        + "(shows ghost blocks the player can build, or a printer mod can auto-build). "
                        + "Set paste=true to instantly place all blocks into the world (singleplayer creative only).",
                McpTool.schema("{"
                        + "\"name\":{\"type\":\"string\",\"description\":\"schematic name used in create_litematic (or filename without .litematic)\"},"
                        + "\"origin\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"},\"description\":\"world [x,y,z] of the schematic min corner\"},"
                        + "\"paste\":{\"type\":\"boolean\",\"description\":\"instantly place blocks (singleplayer creative only)\"}"
                        + "}", "name", "origin"),
                args -> {
                    if (!FabricLoader.getInstance().isModLoaded("litematica")) {
                        return "Litematica mod is not loaded — the .litematic file was still saved in "
                                + schematicsDir() + " and can be loaded manually.";
                    }
                    String name = args.get("name").getAsString();
                    JsonArray o = args.getAsJsonArray("origin");
                    int[] origin = xyz(o);
                    boolean paste = args.has("paste") && args.get("paste").getAsBoolean();
                    Path file = schematicsDir().resolve(name.endsWith(".litematic") ? name : name + ".litematic");
                    return ClientThread.call(() -> {
                        try {
                            return LitematicaCompat.loadAndPlace(file, origin[0], origin[1], origin[2], paste);
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                });
    }

    private static int[] xyz(JsonArray arr) {
        if (arr == null || arr.size() != 3) throw new IllegalArgumentException("Expected [x,y,z]");
        return new int[]{arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt()};
    }

    private static Map<String, String> props(JsonObject obj) {
        if (!obj.has("props")) return Map.of();
        Map<String, String> props = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("props").entrySet()) {
            props.put(e.getKey(), e.getValue().getAsString());
        }
        return props;
    }

    private static void put(Map<Long, LitematicWriter.Entry> blocks, int x, int y, int z,
                            String block, Map<String, String> props) {
        if (x < 0 || y < 0 || z < 0) throw new IllegalArgumentException("Coordinates must be >= 0 (relative): " + x + "," + y + "," + z);
        if (x > 511 || y > 383 || z > 511) throw new IllegalArgumentException("Schematic too large at " + x + "," + y + "," + z);
        long key = ((long) x << 40) | ((long) y << 20) | z;
        blocks.put(key, new LitematicWriter.Entry(x, y, z, block, props));
    }
}
