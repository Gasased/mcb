package dev.builderai.claudebridge.tools;

import com.google.gson.JsonObject;
import dev.builderai.claudebridge.mcp.McpTool;

public final class SimpleTool implements McpTool {
    @FunctionalInterface
    public interface Impl {
        String call(JsonObject args) throws Exception;
    }

    private final String name;
    private final String description;
    private final JsonObject schema;
    private final Impl impl;

    public SimpleTool(String name, String description, JsonObject schema, Impl impl) {
        this.name = name;
        this.description = description;
        this.schema = schema;
        this.impl = impl;
    }

    @Override public String name() { return name; }
    @Override public String description() { return description; }
    @Override public JsonObject inputSchema() { return schema; }
    @Override public String call(JsonObject args) throws Exception { return impl.call(args); }
}
