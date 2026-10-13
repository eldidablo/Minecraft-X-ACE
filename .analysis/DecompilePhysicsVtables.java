import ghidra.app.script.GhidraScript;
import ghidra.app.cmd.disassemble.DisassembleCommand;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.decompiler.DecompiledFunction;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DecompilePhysicsVtables extends GhidraScript {
    private static final String[][] TABLES = {
        {"PhysicsEngine", "0x1455ea1c0"},
        {"PhysicsCore", "0x145606230"},
        {"PhysicsCoreExperimental", "0x1456065d8"},
        {"ITyreModel", "0x1456057b8"},
        {"SCTM", "0x1456057d8"},
        {"Suspension", "0x1455fb1c8"},
        {"SuspensionML", "0x1455fb908"},
        {"SuspensionStrut", "0x1455fb6a8"},
        {"SuspensionWishboneCoil", "0x1455fc4c8"},
        {"SuspensionTrailingArm", "0x1455fc048"}
    };
    private static int u(byte b) { return b & 0xff; }
    private static long readU32(byte[] b, int p) {
        return ((long)u(b[p])) | ((long)u(b[p+1]) << 8) | ((long)u(b[p+2]) << 16) | ((long)u(b[p+3]) << 24);
    }
    private static long readU64(byte[] b, int p) {
        long v=0;
        for (int i=7;i>=0;i--) v=(v<<8)|u(b[p+i]);
        return v;
    }
    private static String hex(long v) { return "0x" + Long.toHexString(v); }
    private Address[] findPdataRange(byte[] pdataBytes, long imageBase, long fn) {
        long rva=fn-imageBase;
        for (int p=0;p+12<=pdataBytes.length;p+=12) {
            long begin=readU32(pdataBytes,p), end=readU32(pdataBytes,p+4);
            if (begin<=rva && rva<end) return new Address[]{currentProgram.getImageBase().add(begin),currentProgram.getImageBase().add(end-1)};
        }
        return null;
    }
    private Address[] findLeafRange(Address start) throws Exception {
        Address scanEnd=start.add(0x200);
        AddressSet scanSet=new AddressSet(start,scanEnd);
        new DisassembleCommand(start,scanSet,true).applyTo(currentProgram,monitor);
        InstructionIterator it=currentProgram.getListing().getInstructions(scanSet,true);
        Address ret=null;
        while (it.hasNext()) {
            Instruction ins=it.next();
            String mnemonic=ins.getMnemonicString().toUpperCase();
            if (mnemonic.startsWith("RET") || mnemonic.startsWith("JMP") || ins.getFlowType().isTerminal()) { ret=ins.getMaxAddress(); break; }
        }
        if (ret==null) return null;
        return new Address[]{start,ret};
    }
    @Override
    public void run() throws Exception {
        StringBuilder out=new StringBuilder();
        Memory mem=currentProgram.getMemory();
        MemoryBlock text=mem.getBlock(".text"), pdata=mem.getBlock(".pdata");
        byte[] pdataBytes=new byte[(int)pdata.getSize()];
        mem.getBytes(pdata.getStart(),pdataBytes);
        long imageBase=currentProgram.getImageBase().getOffset();
        long textStart=text.getStart().getOffset(), textEnd=text.getEnd().getOffset()+1;
        DecompInterface decomp=new DecompInterface();
        Set<Long> seen=new HashSet<>();
        try {
            if (!decomp.openProgram(currentProgram)) throw new IllegalStateException("Decompiler could not open program: "+decomp.getLastMessage());
            for (String[] table:TABLES) {
                Address vt=toAddr(table[1]);
                byte[] slots=new byte[640];
                mem.getBytes(vt,slots);
                for (int slot=0;slot<80;slot++) {
                    long fn=readU64(slots,slot*8);
                    if (fn<textStart || fn>=textEnd) break;
                    if (!seen.add(fn)) continue;
                    Address entry=toAddr(hex(fn));
                    Address[] range=findPdataRange(pdataBytes,imageBase,fn);
                    String rangeSource="pdata";
                    if (range==null) { range=findLeafRange(entry); rangeSource="ret-scan"; }
                    out.append("\n=== ").append(table[0]).append(" vtable slot ").append(slot).append(" ===\n");
                    if (range==null) {
                        out.append("RANGE_NOT_FOUND|fn=").append(entry).append("\n");
                        continue;
                    }
                    Address start=range[0], end=range[1];
                    AddressSet body=new AddressSet(start,end);
                    Function function=currentProgram.getFunctionManager().getFunctionAt(start);
                    if (function==null) function=createFunction(start,null);
                    if (function==null) { out.append("FUNCTION_CREATION_FAILED|fn=").append(start).append("\n"); continue; }
                    try { function.setBody(body); }
                    catch (Exception e) { out.append("SET_BODY_FAILED|").append(e).append("\n"); }
                    out.append("entry=").append(entry).append(" range=").append(start).append("..").append(end).append(" source=").append(rangeSource).append(" name=").append(function.getName()).append("\n");
                    DecompileResults result=decomp.decompileFunction(function,45,monitor);
                    if (!result.decompileCompleted() || result.getDecompiledFunction()==null) {
                        out.append("DECOMPILE_FAILED: ").append(result.getErrorMessage()).append("\n");
                        continue;
                    }
                    DecompiledFunction pseudocode=result.getDecompiledFunction();
                    out.append(pseudocode.getC()).append("\n");
                }
            }
        } finally { decomp.dispose(); }
        Files.writeString(Paths.get(getScriptArgs()[0]),out.toString(),StandardCharsets.UTF_8);
        println("Wrote deduplicated vtable pseudocode report: "+getScriptArgs()[0]);
    }
}
