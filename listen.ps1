# Blocks until the player types /claude <something> in Minecraft, then prints it and exits.
# Meant to be run by Claude Code as a background process: when this exits, Claude is
# notified, reads the PROMPT line, fulfills the request itself, and restarts this script.
$body = '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"await_chat_prompt","arguments":{"timeout_seconds":300}}}'
while ($true) {
    try {
        $r = Invoke-RestMethod -Uri 'http://127.0.0.1:8756/mcp' -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 320
    } catch {
        Write-Output "waiting: game/MCP not reachable ($($_.Exception.Message)), retrying in 10s..."
        Start-Sleep 10
        continue
    }
    $text = $r.result.content[0].text
    if ($text -and $text -ne 'TIMEOUT') {
        Write-Output "PROMPT: $text"
        exit 0
    }
    # TIMEOUT — keep listening
}
