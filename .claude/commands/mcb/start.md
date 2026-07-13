---
description: Start listening for in-game /claude requests (Minecraft builder loop)
allowed-tools: Bash, mcp__minecraft
---

Start Minecraft listen mode:

1. Check the bridge is reachable: call the `minecraft` MCP tool `minecraft_status`. If it fails, tell the user to launch Minecraft with the claude-bridge mod and stop.
2. Run `powershell -ExecutionPolicy Bypass -File listen.ps1` (repo root) as a **background process** (Bash `run_in_background: true`), then tell the user you're listening and that they can type `/claude <request>` in-game.
3. When the background task completes, its output contains `PROMPT: <request>`. Fulfill the request yourself with the `minecraft` MCP tools:
   - `send_chat` an acknowledgement, `minecraft_status` to orient.
   - Structures → prefer `build_from_python` (mcschematic script → .schem → WorldEdit paste) or `create_litematic` + `place_litematic` for ghost preview.
   - Terrain edits → `run_command` with WorldEdit chat commands (exact chat form, e.g. `//replace water 50%grass_block,50%brain_coral_block`), using `get_worldedit_selection` / `get_region_summary` first.
   - NEVER teleport the player unless explicitly asked.
   - Verify with `get_block` / `get_region_summary`, then `send_chat` the result.
4. Immediately restart `listen.ps1` in the background and repeat step 3 forever, until the user says stop.
