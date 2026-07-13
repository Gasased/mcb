package dev.builderai.claudebridge.tools;

import com.google.gson.JsonArray;
import dev.builderai.claudebridge.mcp.McpTool;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class PythonBuildTools {
    private PythonBuildTools() {}

    public static List<McpTool> all() {
        return List.of(buildFromPython());
    }

    static McpTool buildFromPython() {
        return new SimpleTool("build_from_python",
                "PREFERRED way to build structures: write a Python script that generates a schematic with the "
                        + "'mcschematic' library, and this tool runs it and instantly pastes the result via WorldEdit. "
                        + "Script contract: it must save exactly '<name>.schem' into the current working directory, e.g.\n"
                        + "  import mcschematic\n"
                        + "  s = mcschematic.MCSchematic()\n"
                        + "  s.setBlock((x,y,z), 'minecraft:stone')  # coords relative to paste origin\n"
                        + "  s.save('.', '<name>', mcschematic.Version.JE_1_21_1)\n"
                        + "Requires python + 'pip install mcschematic' on this PC. "
                        + "Set paste=false to only generate the file (then load it via Litematica or //schem load). "
                        + "Do NOT teleport the player.",
                McpTool.schema("{"
                        + "\"name\":{\"type\":\"string\",\"description\":\"schematic name; script must save <name>.schem\"},"
                        + "\"script\":{\"type\":\"string\",\"description\":\"python source code\"},"
                        + "\"origin\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"},\"description\":\"world [x,y,z] where schematic (0,0,0) lands\"},"
                        + "\"paste\":{\"type\":\"boolean\",\"description\":\"paste via WorldEdit after generating (default true)\"}"
                        + "}", "name", "script", "origin"),
                args -> {
                    String name = args.get("name").getAsString().replaceAll("[^a-zA-Z0-9_\\-]", "_");
                    JsonArray o = args.getAsJsonArray("origin");
                    if (o == null || o.size() != 3) throw new IllegalArgumentException("origin must be [x,y,z]");
                    boolean paste = !args.has("paste") || args.get("paste").getAsBoolean();

                    Path workDir = FabricLoader.getInstance().getGameDir().resolve("claude-builds");
                    Files.createDirectories(workDir);
                    Path scriptFile = workDir.resolve(name + ".py");
                    Files.writeString(scriptFile, args.get("script").getAsString());

                    String output = runPython(scriptFile, workDir);
                    Path schem = workDir.resolve(name + ".schem");
                    if (!Files.exists(schem)) {
                        return "Python ran but did not produce " + schem + ".\n--- python output ---\n" + output
                                + "\nMake sure the script calls s.save('.', '" + name + "', ...).";
                    }

                    if (!paste) {
                        return "Generated " + schem + " (not pasted).\n--- python output ---\n" + output;
                    }
                    if (!FabricLoader.getInstance().isModLoaded("worldedit")) {
                        return "Generated " + schem + " but WorldEdit is not loaded — cannot paste. "
                                + "Load it manually or via Litematica.";
                    }
                    String pasteResult = WorldEditCompat.pasteSchematic(
                            schem, o.get(0).getAsInt(), o.get(1).getAsInt(), o.get(2).getAsInt());
                    return pasteResult + (output.isBlank() ? "" : "\n--- python output ---\n" + output);
                });
    }

    private static String runPython(Path script, Path workDir) throws Exception {
        IOException lastError = null;
        for (String python : new String[]{"python", "py", "python3"}) {
            try {
                Process process = new ProcessBuilder(python, script.getFileName().toString())
                        .directory(workDir.toFile())
                        .redirectErrorStream(true)
                        .start();
                String output = new String(process.getInputStream().readAllBytes());
                if (!process.waitFor(120, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new RuntimeException("Python script timed out after 120s.\n" + output);
                }
                if (process.exitValue() != 0) {
                    throw new RuntimeException("Python exited with code " + process.exitValue()
                            + " ('pip install mcschematic' missing?):\n" + output);
                }
                return output;
            } catch (IOException e) {
                lastError = e; // interpreter not found under this name — try the next
            }
        }
        throw new RuntimeException("No python interpreter found (tried python/py/python3). Install Python 3.", lastError);
    }
}
