import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.decompiler.DecompiledFunction;
import ghidra.app.cmd.disassemble.DisassembleCommand;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressSet;
import ghidra.program.model.listing.Function;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class DecompileFocused extends GhidraScript {
    private static final String[][] TARGETS = {
        {"0x1423d88d0", "0x1423dad8b", "slip-and-wheel telemetry serializer"},
        {"0x142372970", "0x142373c14", "torque telemetry serializer"},
        {"0x142b60e80", "0x142b611b4", "VehicleAnalysisPacejkaData serializer"},
        {"0x142b5eac0", "0x142b5ef13", "VehicleAnalysisPacejkaData reader"}
    };
    @Override
    public void run() throws Exception {
        StringBuilder out = new StringBuilder();
        DecompInterface decomp = new DecompInterface();
        try {
            if (!decomp.openProgram(currentProgram)) throw new IllegalStateException("Decompiler could not open program: " + decomp.getLastMessage());
            for (String[] target : TARGETS) {
                Address entry = toAddr(target[0]);
                Address end = toAddr(target[1]);
                AddressSet bounded = new AddressSet(entry, end);
                DisassembleCommand command = new DisassembleCommand(entry, bounded, true);
                boolean disassembled = command.applyTo(currentProgram, monitor);
                Function function = currentProgram.getFunctionManager().getFunctionAt(entry);
                if (function == null) function = createFunction(entry, null);
                out.append("\n=== ").append(target[2]).append(" ===\n");
                out.append("entry=").append(entry).append(" end=").append(end).append(" disassemble=").append(disassembled).append("\n");
                if (function == null) {
                    out.append("FUNCTION_CREATION_FAILED\n");
                    continue;
                }
                out.append("name=").append(function.getName()).append(" body=").append(function.getBody()).append("\n");
                DecompileResults result = decomp.decompileFunction(function, 120, monitor);
                if (!result.decompileCompleted() || result.getDecompiledFunction() == null) {
                    out.append("DECOMPILE_FAILED: ").append(result.getErrorMessage()).append("\n");
                    continue;
                }
                DecompiledFunction pseudocode = result.getDecompiledFunction();
                out.append(pseudocode.getC()).append("\n");
            }
        } finally {
            decomp.dispose();
        }
        Files.writeString(Paths.get(getScriptArgs()[0]), out.toString(), StandardCharsets.UTF_8);
        println("Wrote focused pseudocode report: " + getScriptArgs()[0]);
    }
}
