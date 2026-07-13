package dev.builderai.claudebridge.tools;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Touches Litematica classes — only call when the 'litematica' mod is loaded.
 * Uses Litematica internals (no public API); pinned to litematica 0.28.x for MC 26.2.
 */
final class LitematicaCompat {
    private LitematicaCompat() {}

    /** Runs on the client thread (via ClientThread); the paste itself hops to the server thread. */
    static String loadAndPlace(Path file, int x, int y, int z, boolean paste) throws Exception {
        LitematicaSchematic schematic = LitematicaSchematic.createFromFile(
                file.getParent(), file.getFileName().toString());
        if (schematic == null) {
            return "Failed to load schematic " + file;
        }
        SchematicHolder.getInstance().addSchematic(schematic, true);

        BlockPos origin = new BlockPos(x, y, z);
        SchematicPlacement placement = SchematicPlacement.createFor(
                schematic, origin, file.getFileName().toString(), true, true);
        DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, true);

        if (!paste) {
            return "Placement created at " + origin.toShortString()
                    + " — ghost blocks are now visible. Player can build along them, use a printer mod, "
                    + "or ask me to paste (singleplayer creative).";
        }

        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return "Placement created (ghost blocks visible at " + origin.toShortString()
                    + ") but paste requires singleplayer. Build it manually or with a printer mod.";
        }
        ServerLevel level = server.getLevel(mc.player.level().dimension());
        Vec3i size = schematic.getMetadata().getEnclosingSize();
        BlockPos max = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);

        // placeToWorld writes blocks directly, so it must run on the SERVER thread —
        // the client-side SchematicPlacementManager paste silently no-ops here.
        CompletableFuture<String> result = new CompletableFuture<>();
        server.execute(() -> {
            try {
                long before = countNonAir(level, origin, max);
                boolean ok = schematic.placeToWorld(level, placement, false);
                long after = countNonAir(level, origin, max);
                result.complete(String.format(
                        "placeToWorld returned %s; non-air blocks in %s..%s: %d -> %d (expected ~%d schematic blocks).%s",
                        ok, origin.toShortString(), max.toShortString(), before, after,
                        schematic.getMetadata().getTotalBlocks(),
                        after > before || ok ? "" : " Paste appears to have FAILED — check gamemode/permissions."));
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result.get(60, TimeUnit.SECONDS);
    }

    private static long countNonAir(ServerLevel level, BlockPos min, BlockPos max) {
        long count = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = min.getY(); y <= max.getY(); y++)
            for (int z = min.getZ(); z <= max.getZ(); z++)
                for (int px = min.getX(); px <= max.getX(); px++)
                    if (!level.getBlockState(pos.set(px, y, z)).isAir()) count++;
        return count;
    }
}
