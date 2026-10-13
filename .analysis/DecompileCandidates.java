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

public class DecompileCandidates extends GhidraScript {
    private static final String[][] TARGETS = {
        {"0x1423e8d70", "0x1423e9963", "Brake pass routine"},
        {"0x142239590", "0x14223a027", "Car forcePosition raycast"},
        {"0x142510dd0", "0x142511190", "PhysicsCore creation"},
        {"0x142516580", "0x14251694d", "Experimental PhysicsCore creation"},
        {"0x1424fed90", "0x1424ff06b", "Tyre pressure spring curve"},
        {"0x1424fcd90", "0x1424fd1af", "Tyre thermal performance curve"},
        {"0x142227d10", "0x142228509", "Tyre compound validation"},
        {"0x142208b10", "0x14220a339", "Car construction"},
        {"0x142211f40", "0x1422127b5", "Car teardown or state copy"}
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
                boolean disassembled = new DisassembleCommand(entry, bounded, true).applyTo(currentProgram, monitor);
                Function function = currentProgram.getFunctionManager().getFunctionAt(entry);
                if (function == null) function = createFunction(entry, null);
                out.append("\n=== ").append(target[2]).append(" ===\n");
                out.append("entry=").append(entry).append(" end=").append(end).append(" disassemble=").append(disassembled).append("\n");
                if (function == null) {
                    out.append("FUNCTION_CREATION_FAILED\n");
                    continue;
                }
                try {
                    function.setBody(bounded);
                } catch (Exception e) {
                    out.append("SET_BODY_FAILED: ").append(e).append("\n");
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
        println("Wrote candidate pseudocode report: " + getScriptArgs()[0]);
    }
}
