package dev.builderai.claudebridge.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Minimal MCP server over Streamable HTTP: JSON-RPC 2.0 on POST /mcp.
 * Supports initialize, tools/list, tools/call. Bound to localhost only.
 */
public final class McpServer {
    public static final int PORT = 8756;
    private final Map<String, McpTool> tools = new LinkedHashMap<>();
    private HttpServer server;

    public void register(McpTool tool) {
        tools.put(tool.name(), tool);
    }

    public synchronized void start() throws IOException {
        if (server != null) return;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        // Tool calls can block (long-poll, client-thread hops) — give each request its own thread.
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "claude-bridge-mcp");
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/mcp", this::handle);
        server.start();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                respond(ex, 405, "{\"error\":\"POST only\"}");
                return;
            }
            JsonElement parsed = JsonParser.parseReader(new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8));
            JsonObject req = parsed.getAsJsonObject();
            String method = req.has("method") ? req.get("method").getAsString() : "";
            JsonElement id = req.get("id");

            if (id == null || id.isJsonNull()) { // notification (e.g. notifications/initialized)
                ex.sendResponseHeaders(202, -1);
                ex.close();
                return;
            }

            JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            response.add("id", id);
            try {
                response.add("result", dispatch(method, req.getAsJsonObject("params")));
            } catch (Exception e) {
                JsonObject error = new JsonObject();
                error.addProperty("code", -32603);
                error.addProperty("message", String.valueOf(e.getMessage()));
                response.add("error", error);
            }
            respond(ex, 200, response.toString());
        } catch (Exception e) {
            respond(ex, 400, "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"parse error\"}}");
        }
    }

    private JsonObject dispatch(String method, JsonObject params) throws Exception {
        switch (method) {
            case "initialize": {
                JsonObject result = new JsonObject();
                result.addProperty("protocolVersion",
                        params != null && params.has("protocolVersion") ? params.get("protocolVersion").getAsString() : "2025-06-18");
                JsonObject caps = new JsonObject();
                caps.add("tools", new JsonObject());
                result.add("capabilities", caps);
                JsonObject info = new JsonObject();
                info.addProperty("name", "claude-bridge-minecraft");
                info.addProperty("version", "0.1.0");
                result.add("serverInfo", info);
                return result;
            }
            case "ping":
                return new JsonObject();
            case "tools/list": {
                JsonObject result = new JsonObject();
                JsonArray list = new JsonArray();
                for (McpTool tool : tools.values()) {
                    JsonObject t = new JsonObject();
                    t.addProperty("name", tool.name());
                    t.addProperty("description", tool.description());
                    t.add("inputSchema", tool.inputSchema());
                    list.add(t);
                }
                result.add("tools", list);
                return result;
            }
            case "tools/call": {
                String name = params.get("name").getAsString();
                McpTool tool = tools.get(name);
                if (tool == null) throw new IllegalArgumentException("Unknown tool: " + name);
                JsonObject args = params.has("arguments") && params.get("arguments").isJsonObject()
                        ? params.getAsJsonObject("arguments") : new JsonObject();
                JsonObject result = new JsonObject();
                JsonArray content = new JsonArray();
                JsonObject text = new JsonObject();
                text.addProperty("type", "text");
                try {
                    text.addProperty("text", tool.call(args));
                    result.addProperty("isError", false);
                } catch (Exception e) {
                    text.addProperty("text", "Error: " + e.getMessage());
                    result.addProperty("isError", true);
                }
                content.add(text);
                result.add("content", content);
                return result;
            }
            default:
                throw new IllegalArgumentException("Method not supported: " + method);
        }
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
