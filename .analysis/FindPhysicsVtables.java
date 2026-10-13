import ghidra.app.script.GhidraScript;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.mem.MemoryBlock;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FindPhysicsVtables extends GhidraScript {
    private static final String[] TYPES = {
        ".?AVPhysicsEngine@@", ".?AVIPhysicsCore@@", ".?AVPhysicsCore@@",
        ".?AVPhysicsCoreExperimental@@", ".?AVITyreModel@@", ".?AVCar@@",
        ".?AVSuspension@@", ".?AVSuspensionML@@", ".?AVSuspensionStrut@@",
        ".?AVSuspensionWishboneCoil@@", ".?AVSuspensionTrailingArm@@",
        ".?AVNetcarPhysicalBody@@", ".?AVSuspensionMLNew@@", ".?AVSuspensionStrutMultilink@@",
        ".?AVSuspensionArm@@", ".?AVSuspensionAxle@@", ".?AVSuspensionLink@@"
    };
    private static int u(byte b) { return b & 0xff; }
    private static long readU32(byte[] b, int p) {
        return ((long)u(b[p])) | ((long)u(b[p+1]) << 8) | ((long)u(b[p+2]) << 16) | ((long)u(b[p+3]) << 24);
    }
    private static long readU64(byte[] b, int p) {
        long v = 0;
        for (int i = 7; i >= 0; i--) v = (v << 8) | u(b[p+i]);
        return v;
    }
    private static boolean match(byte[] b, int p, byte[] n) {
        if (p < 0 || p + n.length > b.length) return false;
        for (int i=0;i<n.length;i++) if (b[p+i] != n[i]) return false;
        return true;
    }
    private static String hex(long v) { return "0x" + Long.toHexString(v); }
    @Override
    public void run() throws Exception {
        Memory mem = currentProgram.getMemory();
        MemoryBlock rdata = mem.getBlock(".rdata");
        MemoryBlock dataBlock = mem.getBlock(".data");
        MemoryBlock text = mem.getBlock(".text");
        if (rdata == null || dataBlock == null || text == null) throw new IllegalStateException("Expected PE sections missing");
        byte[] rdataBytes = new byte[(int)rdata.getSize()];
        byte[] dataBytes = new byte[(int)dataBlock.getSize()];
        mem.getBytes(rdata.getStart(), rdataBytes);
        mem.getBytes(dataBlock.getStart(), dataBytes);
        long imageBase = currentProgram.getImageBase().getOffset();
        long textStart = text.getStart().getOffset(), textEnd = text.getEnd().getOffset() + 1;
        for (String type : TYPES) {
            byte[] name = type.getBytes(StandardCharsets.US_ASCII);
            List<Long> descriptors = new ArrayList<>();
            for (MemoryBlock stringBlock : new MemoryBlock[]{rdata, dataBlock}) {
                byte[] stringBytes = stringBlock == rdata ? rdataBytes : dataBytes;
                long blockVA = stringBlock.getStart().getOffset();
                for (int i=0;i<=stringBytes.length-name.length;i++) {
                    if (match(stringBytes,i,name) && (i + name.length == stringBytes.length || stringBytes[i+name.length] == 0)) descriptors.add(blockVA+i-16);
                }
            }
            println("TYPE|" + type + "|descriptors=" + descriptors.size());
            for (long descVA : descriptors) {
                long descRva = descVA - imageBase;
                byte[] rvaNeedle = new byte[]{(byte)descRva, (byte)(descRva>>>8), (byte)(descRva>>>16), (byte)(descRva>>>24)};
                for (MemoryBlock scanBlock : new MemoryBlock[]{rdata, dataBlock}) {
                    byte[] scanBytes = scanBlock == rdata ? rdataBytes : dataBytes;
                    long blockVA = scanBlock.getStart().getOffset();
                    for (int p=0;p+24<=scanBytes.length;p+=4) {
                        if (!match(scanBytes,p+12,rvaNeedle)) continue;
                        long colVA = blockVA+p;
                        long signature = readU32(scanBytes,p), offset = readU32(scanBytes,p+4), cdOffset = readU32(scanBytes,p+8);
                        long hierarchyRva = readU32(scanBytes,p+16), selfRva = readU32(scanBytes,p+20);
                        if (signature != 1 || selfRva != colVA-imageBase) continue;
                        println("COL|" + type + "|col=" + hex(colVA) + "|td=" + hex(descVA) + "|offset=" + offset + "|cd=" + cdOffset + "|hierarchy=" + hex(imageBase+hierarchyRva));
                        byte[] colPointer = new byte[8];
                        for (int j=0;j<8;j++) colPointer[j] = (byte)(colVA >>> (8*j));
                        for (MemoryBlock refBlock : new MemoryBlock[]{rdata, dataBlock}) {
                            byte[] refBytes = refBlock == rdata ? rdataBytes : dataBytes;
                            long refBase = refBlock.getStart().getOffset();
                            for (int q=0;q+16<=refBytes.length;q+=8) {
                                if (!match(refBytes,q,colPointer)) continue;
                                long vfptr = refBase+q+8;
                                println("VTABLE_START|" + type + "|" + hex(vfptr) + "|col=" + hex(colVA));
                                for (int k=0;k<80 && q+8+8*k+8<=refBytes.length;k++) {
                                    long fn = readU64(refBytes,q+8+8*k);
                                    if (fn < textStart || fn >= textEnd) break;
                                    println("VTABLE|" + type + "|" + hex(vfptr) + "|slot=" + k + "|fn=" + hex(fn));
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
