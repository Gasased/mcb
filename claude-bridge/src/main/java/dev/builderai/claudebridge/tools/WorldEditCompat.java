package dev.builderai.claudebridge.tools;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Touches WorldEdit classes — only call when the 'worldedit' mod is loaded and we run
 * on an integrated server, otherwise class loading fails.
 */
final class WorldEditCompat {
    private WorldEditCompat() {}

    static String describeSelection() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return "No integrated server";
        ServerPlayer serverPlayer = server.getPlayerList()
                .getPlayer(Minecraft.getInstance().player.getUUID());
        if (serverPlayer == null) return "Server-side player not found";

        Player actor = FabricAdapter.get().fromNativePlayer(serverPlayer);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        try {
            Region region = session.getSelection(session.getSelectionWorld());
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            return String.format("min=[%d,%d,%d] max=[%d,%d,%d] size=[%d,%d,%d] volume=%d",
                    min.x(), min.y(), min.z(), max.x(), max.y(), max.z(),
                    max.x() - min.x() + 1, max.y() - min.y() + 1, max.z() - min.z() + 1,
                    region.getVolume());
        } catch (IncompleteRegionException e) {
            return "No selection made yet — ask the player to select a region with the WorldEdit wand (//wand, left/right click) or //pos1 //pos2.";
        }
    }

    /**
     * Pastes a .schem/.schematic file into the world at origin via the WorldEdit API.
     * Safe to call from any thread; the edit itself runs on the server thread.
     */
    static String pasteSchematic(Path file, int x, int y, int z) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return "Paste via WorldEdit API requires singleplayer. On a server, load the schematic with "
                    + "run_command '//schem load " + file.getFileName() + "' then '//paste'.";
        }
        ClipboardFormat format = ClipboardFormats.findByFile(file.toFile());
        if (format == null) return "Unrecognized schematic format: " + file;
        Clipboard clipboard;
        try (var reader = format.getReader(new FileInputStream(file.toFile()))) {
            clipboard = reader.read();
        }

        ServerLevel level = server.getLevel(mc.player.level().dimension());
        CompletableFuture<String> result = new CompletableFuture<>();
        server.execute(() -> {
            try (EditSession es = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(FabricAdapter.get().fromNativeWorld(level))
                    .maxBlocks(-1)
                    .build()) {
                Operation op = new ClipboardHolder(clipboard)
                        .createPaste(es)
                        .to(BlockVector3.at(x, y, z))
                        .ignoreAirBlocks(true)
                        .build();
                Operations.complete(op);
                result.complete("Pasted " + file.getFileName() + " at [" + x + "," + y + "," + z + "] — "
                        + es.getBlockChangeCount() + " blocks changed. Undo with run_command '//undo' if wrong.");
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result.get(60, TimeUnit.SECONDS);
    }
}
