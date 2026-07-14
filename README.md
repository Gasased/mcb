# mcb — Minecraft Builder MCP

![Claude builds "MCB" in glowstone above a temple plaza, with an in-game chat line from Claude](docs/img/header.webp)

*On camera: dolphin statue, starter house, space rocket, goddess statue, 268-block aqueduct, glowing MCB sign, and a coral pond with a leaning palm. All built by Claude from in-game `/claude` prompts — no human placed a block.*

Type `/claude build me a castle` in Minecraft chat — and Claude builds it.

**mcb** is a client-side Fabric mod for **Minecraft 26.2** that embeds an [MCP](https://modelcontextprotocol.io) server inside the game, plus a Claude Code workflow around it. Claude designs structures as schematics (Litematica ghost blocks or instant paste), terraforms with WorldEdit, and reads your world to know what it's doing.

```
You (in game):  /claude replace all water in my selection with a mix of grass and coral
Claude:         [Claude] On it... → //replace water 50%grass_block,50%brain_coral_block → verified ✓
```

## How it works

```
Minecraft (Fabric client)                         Claude Code (terminal)
┌─────────────────────────────┐                  ┌──────────────────────┐
│ claude-bridge mod           │   HTTP JSON-RPC  │  /mcb:start          │
│  /claude cmd → PromptQueue  │◄────────────────►│  listens in bg,      │
│  MCP server :8756/mcp       │    MCP tools     │  builds on request   │
│  Litematica + WorldEdit API │                  │                      │
└─────────────────────────────┘                  └──────────────────────┘
```

The mod hosts `http://127.0.0.1:8756/mcp` with tools for:

| Tool | Purpose |
|---|---|
| `await_chat_prompt` | long-poll for the next in-game `/claude` message |
| `send_chat` | reply to the player as `[Claude]` |
| `minecraft_status` | position, facing, dimension, loaded mods |
| `get_block` / `get_region_blocks` / `get_region_summary` | read the world |
| `get_worldedit_selection` | player's WorldEdit selection (singleplayer) |
| `run_command` | any command as the player — WorldEdit chat form works (`//replace ...`) |
| `build_from_python` | Claude writes an [mcschematic](https://pypi.org/project/mcschematic/) Python script → `.schem` → instant WorldEdit paste |
| `create_litematic` / `place_litematic` | hand-built `.litematic` → Litematica ghost blocks or instant paste |

## Setup

1. **Build the mod**: `cd claude-bridge && gradlew build` (Java 25) → `build/libs/claude-bridge-0.1.0.jar`.
2. **Install mods** (Fabric, Minecraft 26.2): `claude-bridge`, Fabric API, [Litematica](https://modrinth.com/mod/litematica) + [malilib](https://modrinth.com/mod/malilib), [WorldEdit](https://modrinth.com/mod/worldedit). Litematica/WorldEdit are optional but unlock the good stuff. (Companion mod jars are not included in this repo.)
3. **Python pipeline** (optional, for `build_from_python`): `pip install mcschematic`.
4. **Connect Claude**: this repo ships `.mcp.json` pointing Claude Code at the mod. Launch the game, join a world, then in the repo run `claude` and type **`/mcb:start`** — Claude now listens in the background and serves every in-game `/claude <request>`.

Alternative: `.\serve.ps1` runs a standalone headless Claude serving loop (logs to `serve.log`).

## Repo layout

- `claude-bridge/` — the Fabric mod (see its `README.md` / `CLAUDE.md` for internals: minimal MCP-over-HTTP server, hand-rolled `.litematic` writer, WorldEdit/Litematica API integration)
- `.claude/commands/mcb/start.md` — the `/mcb:start` command
- `listen.ps1` / `serve.ps1` — background listener / detached serving loop
- `CLAUDE.md` — rules Claude follows (tool preferences, never teleport the player, verify every edit)

## Notes

- Singleplayer creative gives the full experience (instant paste, selection reading). On servers, Claude falls back to WorldEdit chat commands and Litematica ghost placements.
- Everything works with **stock** Litematica/WorldEdit — no patches to other mods.
