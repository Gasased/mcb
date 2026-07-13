package dev.builderai.claudebridge;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.builderai.claudebridge.mcp.McpServer;
import dev.builderai.claudebridge.tools.LitematicTools;
import dev.builderai.claudebridge.tools.PlayerTools;
import dev.builderai.claudebridge.tools.PythonBuildTools;
import dev.builderai.claudebridge.tools.WorldTools;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClaudeBridgeClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("claude-bridge");
    private final McpServer mcpServer = new McpServer();

    @Override
    public void onInitializeClient() {
        PlayerTools.all().forEach(mcpServer::register);
        WorldTools.all().forEach(mcpServer::register);
        LitematicTools.all().forEach(mcpServer::register);
        PythonBuildTools.all().forEach(mcpServer::register);
        try {
            mcpServer.start();
            LOGGER.info("Claude Bridge MCP server listening on http://127.0.0.1:{}/mcp", McpServer.PORT);
        } catch (Exception e) {
            LOGGER.error("Failed to start MCP server on port {}", McpServer.PORT, e);
        }

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("claude")
                        .then(ClientCommands.argument("prompt", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String prompt = StringArgumentType.getString(ctx, "prompt");
                                    PromptQueue.push(prompt);
                                    ctx.getSource().sendFeedback(
                                            Component.literal("[Claude] ").withStyle(ChatFormatting.AQUA)
                                                    .append(Component.literal("Request queued: \"" + prompt
                                                            + "\" — make sure a Claude Code session is connected and waiting on await_chat_prompt.")
                                                            .withStyle(ChatFormatting.GRAY)));
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(
                                    Component.literal("[Claude] ").withStyle(ChatFormatting.AQUA)
                                            .append(Component.literal("Usage: /claude <what you want built or changed>. MCP endpoint: http://127.0.0.1:"
                                                    + McpServer.PORT + "/mcp").withStyle(ChatFormatting.GRAY)));
                            return 1;
                        })));
    }
}
