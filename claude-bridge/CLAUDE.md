# claude-bridge/ — the Fabric mod: /claude command + embedded MCP server

The most important file is **`src/main/java/dev/builderai/claudebridge/ClaudeBridgeClient.java`** — client entrypoint: registers all MCP tools, starts `McpServer` on port 8756, registers the `/claude <prompt>` client command (pushes to `PromptQueue`).

## ⚠️ Hard rules
- **Never** call Minecraft world/player APIs from a tool's `call()` directly — tools run on the HTTP thread. **Always** wrap in `ClientThread.call(supplier)` / `ClientThread.run(runnable)` (30s timeout, hops to the client thread).
- **Never** load `WorldEditCompat` / `LitematicaCompat` classes unless `FabricLoader.getInstance().isModLoaded(...)` says the mod is present — they reference compileOnly classes and classloading crashes otherwise. Keep all worldedit/litematica imports confined to those two files.
- **Litematica has NO public API** — `LitematicaCompat` uses internals (`LitematicaSchematic`, `SchematicHolder`, `SchematicPlacement.createFor`, `DataManager.getSchematicPlacementManager()`), pinned to litematica 0.28.x. Verify signatures with `javap` on version bumps.
- **MC 26.2 renames applied — keep them**: `ClientCommands` (not `ClientCommandManager`), `ResourceKey.identifier()`, `Player.sendSystemMessage(Component)` (no boolean arg), `FabricAdapter.get().fromNativePlayer(serverPlayer)`.

## Layout
- `mcp/McpServer.java` — minimal JSON-RPC 2.0 over POST `/mcp` (Streamable HTTP); handles `initialize`, `tools/list`, `tools/call`; binds 127.0.0.1:8756; cached thread pool because tools block (long-poll).
- `mcp/McpTool.java` + `tools/SimpleTool.java` — tool interface / lambda adapter. `McpTool.schema(propsJson, required...)` builds input schemas.
- `PromptQueue.java` — `LinkedBlockingQueue` bridging `/claude` chat → `await_chat_prompt` long-poll.
- `tools/PlayerTools.java` — `minecraft_status`, `await_chat_prompt`, `send_chat`, `run_command`. run_command takes the EXACT chat form (`//set stone`, `/fill ...`) and strips exactly one leading slash before `sendCommand()` — WorldEdit command names start with `/`, so `//cyl` must reach the server as command `/cyl`. Waits 500ms after sending.
- `tools/PythonBuildTools.java` — `build_from_python`: writes the model's mcschematic Python script to `<gameDir>/claude-builds/`, runs python (tries python/py/python3, 120s timeout), expects `<name>.schem`, pastes via `WorldEditCompat.pasteSchematic`. PREFERRED build path.
- `tools/WorldTools.java` — `get_block`, `get_region_summary` (max 500k volume), `get_region_blocks` (max 8k), `get_worldedit_selection` (singleplayer only — reads server-side `LocalSession`).
- `tools/LitematicTools.java` — `create_litematic` (fills applied first, then blocks override; relative coords ≥0; writes to `<gameDir>/schematics/`), `place_litematic` (placement = ghost blocks; `paste:true` = instant, SP only). Paste runs `schematic.placeToWorld(ServerLevel, placement, false)` **on the server thread** and reports the measured non-air block delta — the client-side `pastePlacementToWorld` silently no-ops (BUGLOG bug 2).
- `tools/WorldEditCompat.java` — WE selection read + `pasteSchematic(Path, x,y,z)`: loads .schem via `ClipboardFormats`, pastes with an `EditSession` on the server thread, reports `getBlockChangeCount()`.
- `litematic/LitematicWriter.java` — hand-rolled .litematic NBT (format **Version 6**): palette index 0 = air, index order `(y*sizeZ+z)*sizeX+x`, bit-packed longs **spanning long boundaries** (unlike vanilla chunks), bits = max(2, ceil(log2(paletteSize))). Format ref: litemapy docs.

## Build & dev run
- Build: `$env:JAVA_HOME='C:\Users\novom\.jdks\jbr-25.0.3'; .\gradlew.bat build` → `build/libs/claude-bridge-0.1.0.jar`.
- Dev client: `.\gradlew.bat runClient` (same JAVA_HOME) — loads `run/mods/` (litematica, malilib, worldedit already there). Fabric API comes from the classpath.
- deps: litematica/malilib/worldedit are **compileOnly** (mavens in `build.gradle`: masa.dy.fi/sakura-ryoko, fallenbreath, enginehub); mod must keep working when they're absent at runtime.
- `run/` is the dev game dir — gitignore-worthy except `run/mods/`.
