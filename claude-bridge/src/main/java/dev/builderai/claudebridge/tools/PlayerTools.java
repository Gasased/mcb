package dev.builderai.claudebridge.tools;

import com.google.gson.JsonObject;
import dev.builderai.claudebridge.ClientThread;
import dev.builderai.claudebridge.PromptQueue;
import dev.builderai.claudebridge.mcp.McpTool;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class PlayerTools {
    private PlayerTools() {}

    public static List<McpTool> all() {
        return List.of(status(), awaitPrompt(), sendChat(), runCommand());
    }

    private static LocalPlayer player() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) throw new IllegalStateException("No player — not in a world yet");
        return p;
    }

    static McpTool status() {
        return new SimpleTool("minecraft_status",
                "Get player position, facing, dimension, game mode, whether this is singleplayer, and which helper mods (litematica, worldedit) are loaded. Call this first to orient yourself.",
                McpTool.schema("{}"),
                args -> ClientThread.call(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    LocalPlayer p = player();
                    JsonObject out = new JsonObject();
                    out.addProperty("player", p.getName().getString());
                    JsonObject pos = new JsonObject();
                    pos.addProperty("x", p.getX());
                    pos.addProperty("y", p.getY());
                    pos.addProperty("z", p.getZ());
                    out.add("position", pos);
                    out.addProperty("block_pos", p.blockPosition().toShortString());
                    out.addProperty("yaw", p.getYRot());
                    out.addProperty("pitch", p.getXRot());
                    out.addProperty("facing", p.getDirection().getName());
                    out.addProperty("dimension", p.level().dimension().identifier().toString());
                    out.addProperty("singleplayer", mc.hasSingleplayerServer());
                    out.addProperty("litematica_loaded", FabricLoader.getInstance().isModLoaded("litematica"));
                    out.addProperty("worldedit_loaded", FabricLoader.getInstance().isModLoaded("worldedit"));
                    out.addProperty("pending_prompts", PromptQueue.pending());
                    return out.toString();
                }));
    }

    static McpTool awaitPrompt() {
        return new SimpleTool("await_chat_prompt",
                "Wait (long-poll) for the next /claude <request> the player types in Minecraft chat. Returns the prompt text, or 'TIMEOUT' if none arrives within timeout_seconds (default 60, max 300). Loop on this tool to serve the player continuously.",
                McpTool.schema("{\"timeout_seconds\":{\"type\":\"integer\",\"description\":\"seconds to wait, default 60\"}}"),
                args -> {
                    int timeout = args.has("timeout_seconds") ? args.get("timeout_seconds").getAsInt() : 60;
                    timeout = Math.max(1, Math.min(300, timeout));
                    String prompt = PromptQueue.poll(timeout);
                    return prompt != null ? prompt : "TIMEOUT";
                });
    }

    static McpTool sendChat() {
        return new SimpleTool("send_chat",
                "Show a [Claude] message to the player in their Minecraft chat (client-side only, not broadcast). Use to acknowledge requests and report progress/results.",
                McpTool.schema("{\"message\":{\"type\":\"string\"}}", "message"),
                args -> {
                    String message = args.get("message").getAsString();
                    ClientThread.run(() -> player().sendSystemMessage(
                            Component.literal("[Claude] ").withStyle(ChatFormatting.AQUA)
                                    .append(Component.literal(message).withStyle(ChatFormatting.WHITE))));
                    return "sent";
                });
    }

    static McpTool runCommand() {
        return new SimpleTool("run_command",
                "Run a command as the player. Pass EXACTLY what you would type in Minecraft chat, including slashes: "
                        + "'//replace water 50%grass_block,50%brain_coral_block', '//pos1 -12,52,-6', '//undo', '/fill ...', '/setblock ...'. "
                        + "WorldEdit commands keep their double slash. Waits ~0.5s for the command to apply before returning. "
                        + "NEVER teleport the player (/tp) unless they explicitly asked to be moved.",
                McpTool.schema("{\"command\":{\"type\":\"string\",\"description\":\"exact chat form, e.g. //set stone or /fill ...\"}}", "command"),
                args -> {
                    String command = args.get("command").getAsString().trim();
                    // sendCommand() takes the command WITHOUT the chat's leading slash. WorldEdit
                    // command names themselves start with '/' (chat '//cyl' = command '/cyl'),
                    // so strip exactly one leading slash and keep the rest.
                    String cmd = command.startsWith("/") ? command.substring(1) : command;
                    ClientThread.run(() -> player().connection.sendCommand(cmd));
                    // Give the server a moment to process before the model reads world state.
                    Thread.sleep(500);
                    String pos = ClientThread.call(() -> player().blockPosition().toShortString());
                    return "Sent chat command: /" + cmd + " — player now at [" + pos
                            + "]. Verify the effect with get_block/get_region_summary if it matters.";
                });
    }
}
