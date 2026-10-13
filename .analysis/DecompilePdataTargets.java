import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.decompiler.DecompiledFunction;
import ghidra.app.cmd.disassemble.DisassembleCommand;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressSet;
import ghidra.program.model.listing.Function;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.mem.MemoryBlock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class DecompilePdataTargets extends GhidraScript {
    private static final String[][] TARGETS = {
        {"0x142512960", "PhysicsCore main callback A"},
        {"0x142515ea0", "PhysicsCore main callback B"},
        {"0x1425142d0", "PhysicsCore main callback C"},
        {"0x142516f70", "Experimental PhysicsCore callback A"},
        {"0x142519d10", "Experimental PhysicsCore callback B"},
        {"0x142518630", "Experimental PhysicsCore callback C"},
        {"0x1425140a0", "PhysicsCore component constructor"},
        {"0x1425134c0", "PhysicsCore component factory"},
        {"0x1425185c0", "Experimental PhysicsCore component constructor"},
        {"0x1425179e0", "Experimental PhysicsCore component factory"},
        {"0x1400fb3c0", "SCTM tire model virtual method"},
        {"0x1400fb86b", "SCTM destructor"},
        {"0x1424ea8f0", "Suspension compression/rebound force update"},
        {"0x142500090", "Tyre compound thermal/pressure data update"},
        {"0x14237cf5a", "Engine/drivetrain output state update"},
        {"0x1422e1160", "Differential spool activation routine"}
    };
    private static int u(byte b) { return b & 0xff; }
    private static long readU32(byte[] b, int p) {
        return ((long)u(b[p])) | ((long)u(b[p+1]) << 8) | ((long)u(b[p+2]) << 16) | ((long)u(b[p+3]) << 24);
    }
    private Address[] findRange(Address target) throws Exception {
        Memory mem = currentProgram.getMemory();
        MemoryBlock pdata = mem.getBlock(".pdata");
        if (pdata == null) return null;
        byte[] bytes = new byte[(int)pdata.getSize()];
        mem.getBytes(pdata.getStart(), bytes);
        long rva = target.getOffset() - currentProgram.getImageBase().getOffset();
        for (int p = 0; p + 12 <= bytes.length; p += 12) {
            long begin = readU32(bytes, p), end = readU32(bytes, p + 4);
            if (begin <= rva && rva < end) {
                Address start = currentProgram.getImageBase().add(begin);
                Address last = currentProgram.getImageBase().add(end - 1);
                return new Address[]{start, last};
            }
        }
        return null;
    }
    @Override
    public void run() throws Exception {
        StringBuilder out = new StringBuilder();
        DecompInterface decomp = new DecompInterface();
        try {
            if (!decomp.openProgram(currentProgram)) throw new IllegalStateException("Decompiler could not open program: " + decomp.getLastMessage());
            for (String[] target : TARGETS) {
                Address requested = toAddr(target[0]);
                Address[] range = findRange(requested);
                out.append("\n=== ").append(target[1]).append(" ===\n");
                if (range == null) {
                    out.append("PDR_RANGE_NOT_FOUND|target=").append(requested).append("\n");
                    continue;
                }
                Address start = range[0], end = range[1];
                AddressSet bounded = new AddressSet(start, end);
                boolean disassembled = new DisassembleCommand(start, bounded, true).applyTo(currentProgram, monitor);
                Function function = currentProgram.getFunctionManager().getFunctionAt(start);
                if (function == null) function = createFunction(start, null);
                out.append("requested=").append(requested).append(" range=").append(start).append("..").append(end).append(" disassemble=").append(disassembled).append("\n");
                if (function == null) {
                    out.append("FUNCTION_CREATION_FAILED\n");
                    continue;
                }
                try { function.setBody(bounded); }
                catch (Exception e) { out.append("SET_BODY_FAILED: ").append(e).append("\n"); }
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
        println("Wrote PDATA target pseudocode report: " + getScriptArgs()[0]);
    }
}
