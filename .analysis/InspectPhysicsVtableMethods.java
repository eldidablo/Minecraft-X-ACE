import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.mem.MemoryBlock;

public class InspectPhysicsVtableMethods extends GhidraScript {
    private static final String[][] TABLES = {
        {"PhysicsCore", "0x145606230"},
        {"PhysicsCoreExperimental", "0x1456065d8"},
        {"ITyreModel", "0x1456057b8"}
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
    private Address[] findRange(byte[] pdataBytes, long imageBase, long fn) {
        long rva=fn-imageBase;
        for (int p=0;p+12<=pdataBytes.length;p+=12) {
            long begin=readU32(pdataBytes,p), end=readU32(pdataBytes,p+4);
            if (begin<=rva && rva<end) return new Address[]{currentProgram.getImageBase().add(begin),currentProgram.getImageBase().add(end-1)};
        }
        return null;
    }
    @Override
    public void run() throws Exception {
        Memory mem=currentProgram.getMemory();
        MemoryBlock text=mem.getBlock(".text"), pdata=mem.getBlock(".pdata");
        byte[] pdataBytes=new byte[(int)pdata.getSize()];
        mem.getBytes(pdata.getStart(),pdataBytes);
        long imageBase=currentProgram.getImageBase().getOffset();
        long textStart=text.getStart().getOffset(), textEnd=text.getEnd().getOffset()+1;
        for (String[] table:TABLES) {
            Address vt=toAddr(table[1]);
            byte[] slots=new byte[640];
            mem.getBytes(vt,slots);
            for (int slot=0;slot<80;slot++) {
                long fn=readU64(slots,slot*8);
                if (fn<textStart || fn>=textEnd) break;
                Address[] range=findRange(pdataBytes,imageBase,fn);
                if (range==null) println("METHOD|"+table[0]+"|slot="+slot+"|fn="+hex(fn)+"|range=none");
                else println("METHOD|"+table[0]+"|slot="+slot+"|fn="+hex(fn)+"|range="+range[0]+".."+range[1]+"|bytes="+(range[1].getOffset()-range[0].getOffset()+1));
            }
        }
    }
}
