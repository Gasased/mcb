# MCP Minecraft bridge — bug log (2026-07-14)

Session: singleplayer, player `_Jseven_`, overworld, flat sandstone world.
`minecraft_status` reports `litematica_loaded: true`, `worldedit_loaded: true`.

## Bug 1 — WorldEdit commands are silently no-ops via `run_command`

Sent (per tool docs: chat form minus the first slash):

| tool input | echoed as | effect in world |
|---|---|---|
| `/cyl air 6,5 4` | `Command sent: /cyl air 6,5 4` | none |
| `//cyl air 6,5 4` | `Command sent: ///cyl air 6,5 4` | none (extra slash) |
| `/pos1 -12,52,-6` | `/pos1 -12,52,-6` | none |
| `/pos2 0,55,6` | `/pos2 0,55,6` | none |

After `/pos1` + `/pos2`, `get_worldedit_selection` still returned
"No selection made yet" — so WorldEdit never saw the commands.

Observations:
- The echo shows the tool **always prepends a `/`**, so a WorldEdit command
  needs to be passed as `/cyl ...` — which is what I did, and nothing happened.
  Passing `//cyl ...` produces `///cyl ...`, which is definitely wrong.
  Either the prepend or the echo is lying about what is actually sent.
- Vanilla commands through the same path **do work**: `tp -6 52 0` moved the
  player, and `fill -12 53 0 0 55 0 water` placed water (verified with
  `get_block` → `minecraft:water[level=0]`).

So: vanilla command dispatch OK, WorldEdit command dispatch broken.
Suspect the commands are being sent on the wrong path (server command
dispatcher instead of client chat), which vanilla accepts and WorldEdit
(client-side chat handler) never sees.

Also note: `run_command` returns "Command sent" immediately and does not wait
for the command to take effect. A `tp` followed straight away by a
position-relative command ran at the *old* position. Some kind of ack/flush
would help.

## Bug 2 — `place_litematic` with `paste: true` does nothing

Steps:
1. `create_litematic` name `pond`, 22 fills → saved OK:
   `...\ModrinthApp\profiles\Builder ai\schematics\pond.litematic (13x4x11, 380 blocks)`
2. `place_litematic` name `pond`, origin `[-12, 52, -5]`, `paste: true`
   → returned "Schematic pasted into the world at -12, 52, -5."
3. Verified with `get_region_summary([-12,52,-5] .. [0,55,5])`:
   `sandstone: 374, water: 198` — **zero sand**, and the 198 water is exactly
   what my earlier vanilla `fill` rows placed. `get_block([-12,52,0])` (should
   be `minecraft:sand` from the schematic) → `minecraft:sandstone`.
4. Ran `gamemode creative`, pasted again → identical result, still no change.

So `paste: true` reports success but writes nothing. `paste: false` (ghost
placement) *does* work — ghost blocks appeared.

Repro is deterministic: create any litematic, paste it, diff the region
summary before/after.

## Suggested fixes
- Route WorldEdit commands through the client chat handler (send the literal
  `//cyl ...` text as chat), and stop double-prepending slashes.
- Make `place_litematic(paste=true)` actually invoke Litematica's schematic
  paste (or WorldEdit paste) and **verify + report** the block delta instead of
  unconditionally returning "pasted".
- Have `run_command` wait for the command to be processed before returning.
