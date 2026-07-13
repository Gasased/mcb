package dev.builderai.claudebridge.mcp;

import com.google.gson.JsonObject;

/** One MCP tool: metadata plus an implementation. call() runs on the HTTP server thread. */
public interface McpTool {
    String name();

    String description();

    /** JSON Schema for the tool's arguments. */
    JsonObject inputSchema();

    /** Returns the text result shown to the model. Throw with a helpful message on failure. */
    String call(JsonObject args) throws Exception;

    static JsonObject schema(String propsJson, String... required) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", com.google.gson.JsonParser.parseString(propsJson).getAsJsonObject());
        if (required.length > 0) {
            com.google.gson.JsonArray req = new com.google.gson.JsonArray();
            for (String r : required) req.add(r);
            schema.add("required", req);
        }
        return schema;
    }
}
