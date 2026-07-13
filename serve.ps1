# Starts a headless Claude session that serves in-game /claude requests.
# Run like a dev server (e.g. `bun dev`): start it, leave it running, Ctrl+C to stop.
# Requires: Minecraft running with the claude-bridge mod (MCP at http://127.0.0.1:8756/mcp).
Set-Location $PSScriptRoot

$prompt = @'
Serve my Minecraft requests. Loop forever:
1. Call the minecraft MCP tool await_chat_prompt (timeout_seconds 300). On TIMEOUT, call it again — never stop.
2. For each prompt: acknowledge via send_chat, call minecraft_status to orient, then fulfill it.
   - Building structures: PREFER build_from_python (write an mcschematic Python script, it is generated and pasted via WorldEdit). Alternative: create_litematic + place_litematic for ghost-block previews.
   - Terrain edits/replacements: run_command with WorldEdit chat commands (e.g. //replace), using get_worldedit_selection / get_region_summary first.
   - NEVER teleport the player unless they explicitly ask to be moved.
3. Verify the result (get_block / get_region_summary) and report via send_chat.
'@

# Log to serve.log so a foreground Claude session can check progress.
claude -p $prompt --allowedTools "mcp__minecraft" --verbose 2>&1 | Tee-Object -FilePath "$PSScriptRoot\serve.log"
