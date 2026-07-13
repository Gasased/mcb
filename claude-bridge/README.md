# Claude Bridge — Claude builds in Minecraft via MCP

A client-side Fabric mod for **Minecraft 26.2** that lets you type `/claude <request>` in chat and have Claude Code act on your world:

- **Build structures** — Claude designs a schematic, saves it as `.litematic`, loads it into **Litematica** (ghost blocks / instant paste in singleplayer).
- **Terraform** — Claude drives **WorldEdit** (`//replace water 50%grass_block,50%brain_coral_block`, etc.) via commands, and can read your WorldEdit selection.
- **Inspect** — player position/facing, block lookup, region block listings and composition summaries.

## How it works

The mod embeds a tiny **MCP server** at `http://127.0.0.1:8756/mcp`. Claude Code connects to it (config already in `../.mcp.json`). When you type `/claude build me a castle`, the prompt lands in a queue; a Claude Code session looping on the `await_chat_prompt` tool picks it up, uses the other tools to build, and replies in your chat via `send_chat`.

## Requirements (put in `mods/`)

- Fabric Loader 0.19.x for Minecraft 26.2 + Fabric API
- `litematica-fabric-26.2-0.28.3.jar` **and matching malilib** (litematica requires malilib ≥0.29.2)
- `worldedit-mod-7.4.4.jar`
- `claude-bridge-0.1.0.jar` (build: `gradlew.bat build`, output in `build/libs/`)

## Usage

1. Start Minecraft, join a world (singleplayer creative gives full features: selection reading + instant paste).
2. In this repo run `claude` and say: *"Loop on the minecraft await_chat_prompt tool and serve my in-game requests."*
3. In Minecraft: `/claude build me a small castle here` or `/claude replace all water in my selection with a mix of grass and coral`.

## MCP tools

| Tool | Purpose |
|---|---|
| `minecraft_status` | player pos/facing/dimension, singleplayer?, mods loaded |
| `await_chat_prompt` | long-poll for the next `/claude` message |
| `send_chat` | show a `[Claude]` message to the player |
| `run_command` | run any command as the player (WorldEdit: `//set stone` → pass `/set stone`) |
| `get_block` / `get_region_blocks` / `get_region_summary` | read world blocks |
| `get_worldedit_selection` | current WorldEdit selection (singleplayer) |
| `create_litematic` | build a `.litematic` from fills + blocks, saved to `schematics/` |
| `place_litematic` | load into Litematica at an origin; `paste:true` = instant place (SP creative) |
