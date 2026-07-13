# BuilderAI — Claude builds in Minecraft via a Fabric mod + MCP
Fabric client mod for **Minecraft 26.2** (unobfuscated, Mojang names — NOT yarn-mapped 1.x-era Fabric) + Litematica + WorldEdit. Java 25, Loom 1.17.

Per-folder CLAUDE.md auto-loads with detail; this file is only a map. Don't guess APIs — MC 26.2 renamed things; check the actual jars.

## Non-negotiables
- **The MCP server lives INSIDE the game** (`http://127.0.0.1:8756/mcp`, config in `.mcp.json`). You cannot start it from the CLI — the user must launch Minecraft with the mod. If the `minecraft` MCP tools fail to connect, the game isn't running.
- **Serving mode — default is background**: when the user asks to serve/listen/"start the server", launch `serve.ps1` as a **background process** (Bash `run_in_background`: `powershell -ExecutionPolicy Bypass -File serve.ps1`), confirm it's running, and stay available for normal conversation; the background headless session handles in-game `/claude` requests. Report its progress when the user asks. Only serve inline (loop on `await_chat_prompt` yourself) if the user explicitly wants it in the foreground.
- **Build-tool preference**: structures → `build_from_python` (mcschematic script, auto-pasted via WorldEdit) or `create_litematic`+`place_litematic`; raw WorldEdit `run_command` is for terrain edits/replacements, not structures. **Never `/tp` the player** unless they explicitly ask to be moved.
- **Verify every world change** with `get_block`/`get_region_summary` — paste/commands can silently no-op (see BUGLOG-mcp-minecraft.md history).
- **Never modify the companion mods** (litematica/malilib/worldedit jars) — they are stock references; all fixes go in `claude-bridge/`.
- **Never guess Minecraft/Fabric API names.** 26.2 broke many (e.g. `ClientCommandManager`→`ClientCommands`, `ResourceKey.location()`→`identifier()`). Verify with `javap` against jars in the Gradle cache.
- **World mutations must run on the client thread** — always via `ClientThread.call/run`, never directly from the MCP HTTP thread.
- **Build with JAVA_HOME set**: `$env:JAVA_HOME='C:\Users\novom\.jdks\jbr-25.0.3'; .\gradlew.bat build` in `claude-bridge/`.

## Where do I look?
| I want to… | Read |
|------------|------|
| Mod source, tools, formats, gotchas | [claude-bridge/CLAUDE.md](claude-bridge/CLAUDE.md) |
| Build / run / install / usage | [claude-bridge/README.md](claude-bridge/README.md) |
| Root jars (`litematica-*.jar`, `worldedit-*.jar`) | reference copies of the companion mods; dev runtime uses `claude-bridge/run/mods/` |
