package dev.builderai.claudebridge.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.builderai.claudebridge.ClientThread;
import dev.builderai.claudebridge.mcp.McpTool;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorldTools {
    private WorldTools() {}

    public static List<McpTool> all() {
        return List.of(getBlock(), regionSummary(), regionBlocks(), getSelection());
    }

    private static ClientLevel level() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) throw new IllegalStateException("No world loaded");
        return level;
    }

    private static int[] intArray(JsonObject args, String key) {
        JsonArray arr = args.getAsJsonArray(key);
        if (arr == null || arr.size() != 3) throw new IllegalArgumentException(key + " must be [x,y,z]");
        return new int[]{arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt()};
    }

    static McpTool getBlock() {
        return new SimpleTool("get_block",
                "Get the block state at a world position [x,y,z].",
                McpTool.schema("{\"pos\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"},\"description\":\"[x,y,z]\"}}", "pos"),
                args -> {
                    int[] p = intArray(args, "pos");
                    return ClientThread.call(() ->
                            level().getBlockState(new BlockPos(p[0], p[1], p[2])).toString());
                });
    }

    static McpTool regionSummary() {
        return new SimpleTool("get_region_summary",
                "Count blocks by type inside a world-coordinate box (inclusive corners min..max). Use to understand terrain composition (e.g. how much water) before editing. Max volume 500000.",
                McpTool.schema("{\"min\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"}},\"max\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}}", "min", "max"),
                args -> {
                    int[] min = intArray(args, "min");
                    int[] max = intArray(args, "max");
                    checkVolume(min, max, 500_000);
                    return ClientThread.call(() -> {
                        Map<String, Integer> counts = new LinkedHashMap<>();
                        ClientLevel level = level();
                        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                        for (int y = min[1]; y <= max[1]; y++)
                            for (int z = min[2]; z <= max[2]; z++)
                                for (int x = min[0]; x <= max[0]; x++) {
                                    BlockState state = level.getBlockState(pos.set(x, y, z));
                                    counts.merge(state.getBlock().toString(), 1, Integer::sum);
                                }
                        JsonObject out = new JsonObject();
                        counts.entrySet().stream()
                                .sorted((a, b) -> b.getValue() - a.getValue())
                                .forEach(e -> out.addProperty(e.getKey(), e.getValue()));
                        return out.toString();
                    });
                });
    }

    static McpTool regionBlocks() {
        return new SimpleTool("get_region_blocks",
                "List every non-air block in a world-coordinate box (inclusive corners). Returns lines 'x,y,z minecraft:block[props]' with coordinates relative to min. Max volume 8000 (20x20x20) — use get_region_summary for bigger areas.",
                McpTool.schema("{\"min\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"}},\"max\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}}", "min", "max"),
                args -> {
                    int[] min = intArray(args, "min");
                    int[] max = intArray(args, "max");
                    checkVolume(min, max, 8_000);
                    return ClientThread.call(() -> {
                        StringBuilder sb = new StringBuilder();
                        ClientLevel level = level();
                        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                        for (int y = min[1]; y <= max[1]; y++)
                            for (int z = min[2]; z <= max[2]; z++)
                                for (int x = min[0]; x <= max[0]; x++) {
                                    BlockState state = level.getBlockState(pos.set(x, y, z));
                                    if (!state.isAir()) {
                                        sb.append(x - min[0]).append(',').append(y - min[1]).append(',')
                                          .append(z - min[2]).append(' ').append(state).append('\n');
                                    }
                                }
                        return sb.isEmpty() ? "(all air)" : sb.toString();
                    });
                });
    }

    static McpTool getSelection() {
        return new SimpleTool("get_worldedit_selection",
                "Get the player's current WorldEdit selection (min/max corners, size). Works in singleplayer with the WorldEdit mod installed; on multiplayer servers ask the player for coordinates or use //pos1-style commands instead.",
                McpTool.schema("{}"),
                args -> {
                    if (!FabricLoader.getInstance().isModLoaded("worldedit")) {
                        return "WorldEdit mod is not loaded. Ask the player for region coordinates instead.";
                    }
                    if (!Minecraft.getInstance().hasSingleplayerServer()) {
                        return "Not on an integrated (singleplayer) server — cannot read the WorldEdit session locally. Ask the player for coordinates.";
                    }
                    return ClientThread.call(WorldEditCompat::describeSelection);
                });
    }

    private static void checkVolume(int[] min, int[] max, long limit) {
        long vol = (long) (max[0] - min[0] + 1) * (max[1] - min[1] + 1) * (max[2] - min[2] + 1);
        if (vol <= 0) throw new IllegalArgumentException("max must be >= min on every axis");
        if (vol > limit) throw new IllegalArgumentException("Volume " + vol + " exceeds limit " + limit);
    }
}
