import ghidra.app.script.GhidraScript;
import ghidra.app.cmd.disassemble.DisassembleCommand;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressSet;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.listing.InstructionIterator;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.mem.MemoryBlock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class DecompileSctmHelpers extends GhidraScript {
    private static final String[][] TARGETS = {
        {"0x14015d9b2", "longitudinal/lateral force curve helper"},
        {"0x1400eef26", "tyre temperature or condition scaling helper"},
        {"0x14010d813", "longitudinal force modifier"},
        {"0x1400dd5be", "lateral force modifier"},
        {"0x14001ebaf", "combined-slip helper"},
        {"0x140136d53", "load-dependent helper A"},
        {"0x14017ced9", "load-dependent helper B"},
        {"0x142e37788", "math helper A"},
        {"0x142e3778e", "math helper B"},
        {"0x142e377ca", "math helper C"},
        {"0x142e377a6", "math helper D"}
    };
    private static int u(byte b) { return b & 0xff; }
    private static long readU32(byte[] b, int p) {
        return ((long)u(b[p])) | ((long)u(b[p+1]) << 8) | ((long)u(b[p+2]) << 16) | ((long)u(b[p+3]) << 24);
    }
    private Address[] findRange(Address target) throws Exception {
        MemoryBlock pdata = currentProgram.getMemory().getBlock(".pdata");
        if (pdata == null) return null;
        byte[] bytes = new byte[(int)pdata.getSize()];
        currentProgram.getMemory().getBytes(pdata.getStart(), bytes);
        long rva = target.getOffset() - currentProgram.getImageBase().getOffset();
        for (int p=0;p+12<=bytes.length;p+=12) {
            long begin=readU32(bytes,p), end=readU32(bytes,p+4);
            if (begin<=rva && rva<end)
                return new Address[]{currentProgram.getImageBase().add(begin),currentProgram.getImageBase().add(end-1)};
        }
        return null;
    }
    private Address[] findLeafRange(Address start) throws Exception {
        AddressSet scan = new AddressSet(start,start.add(0x1000));
        new DisassembleCommand(start,scan,true).applyTo(currentProgram,monitor);
        InstructionIterator it=currentProgram.getListing().getInstructions(scan,true);
        while(it.hasNext()) {
            Instruction ins=it.next();
            String m=ins.getMnemonicString().toUpperCase();
            if(m.startsWith("RET") || m.startsWith("JMP") || ins.getFlowType().isTerminal())
                return new Address[]{start,ins.getMaxAddress()};
        }
        return null;
    }
    @Override public void run() throws Exception {
        StringBuilder out=new StringBuilder();
        DecompInterface decomp=new DecompInterface();
        try {
            if(!decomp.openProgram(currentProgram)) throw new IllegalStateException(decomp.getLastMessage());
            for(String[] t:TARGETS) {
                Address target=toAddr(t[0]);
                Address[] range=findRange(target);
                String source="pdata";
                if(range==null) { range=findLeafRange(target); source="leaf scan"; }
                out.append("\n=== ").append(t[1]).append(" ===\ntarget=").append(target).append(" source=").append(source).append("\n");
                Function containing=currentProgram.getFunctionManager().getFunctionContaining(target);
                if(containing!=null) out.append("existingContaining=").append(containing.getEntryPoint()).append(".. ").append(containing.getBody()).append(" name=").append(containing.getName()).append(" thunk=").append(containing.isThunk()).append("\n");
                if(range==null) { out.append("RANGE_NOT_FOUND\n"); continue; }
                Address start=range[0],end=range[1];
                AddressSet body=new AddressSet(start,end);
                new DisassembleCommand(start,body,true).applyTo(currentProgram,monitor);
                Function f=currentProgram.getFunctionManager().getFunctionAt(start);
                if(f==null) f=createFunction(start,null);
                if(f==null) { out.append("FUNCTION_CREATION_FAILED\n"); continue; }
                try { f.setBody(body); } catch(Exception e) { out.append("SET_BODY_FAILED=").append(e).append("\n"); }
                out.append("function=").append(f.getEntryPoint()).append(".. ").append(f.getBody()).append(" name=").append(f.getName()).append(" thunk=").append(f.isThunk()).append("\n");
                if(f.isThunk()) {
                    Function tf=f.getThunkedFunction(true);
                    if(tf!=null) out.append("thunkTarget=").append(tf.getEntryPoint()).append(".. ").append(tf.getBody()).append(" name=").append(tf.getName()).append("\n");
                }
                DecompileResults r=decomp.decompileFunction(f,60,monitor);
                if(!r.decompileCompleted() || r.getDecompiledFunction()==null) out.append("DECOMPILE_FAILED=").append(r.getErrorMessage()).append("\n");
                else out.append(r.getDecompiledFunction().getC()).append("\n");
            }
        } finally { decomp.dispose(); }
        Files.writeString(Paths.get(getScriptArgs()[0]),out.toString(),StandardCharsets.UTF_8);
        println("Wrote SCTM helper pseudocode report: "+getScriptArgs()[0]);
    }
}
