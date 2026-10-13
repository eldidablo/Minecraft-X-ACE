import ghidra.app.script.GhidraScript;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.mem.MemoryBlock;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class FindVehiclePhysicsTypes extends GhidraScript {
    private static int u(byte b) { return b & 0xff; }
    private static long readU64(byte[] b,int p) { long v=0; for(int i=7;i>=0;i--) v=(v<<8)|u(b[p+i]); return v; }
    @Override public void run() throws Exception {
        Memory mem=currentProgram.getMemory();
        MemoryBlock[] readable={mem.getBlock(".rdata"),mem.getBlock(".data")};
        String[] terms={"car","engine","gear","transmission","differential","tyre","tire","wheel","suspension","brake","steer","aero","physics","torque","clutch"};
        long sctmVtable=0x1456057d8L;
        for(MemoryBlock block:readable) {
            if(block==null) continue;
            byte[] b=new byte[(int)block.getSize()]; mem.getBytes(block.getStart(),b);
            for(int i=16;i<b.length-8;i++) {
                if(b[i]!='.' || b[i+1]!='?' || (b[i+2]!='A'&&b[i+2]!='V')) continue;
                int end=i; while(end<b.length&&b[end]!=0&&end-i<256) end++;
                if(end>=b.length||end-i<5) continue;
                String name=new String(b,i,end-i,StandardCharsets.US_ASCII);
                String lower=name.toLowerCase(Locale.ROOT); boolean hit=false;
                for(String term:terms) if(lower.contains(term)) { hit=true; break; }
                if(hit) println("TYPE|"+name+"|descriptor="+block.getStart().add(i-16));
            }
            for(int p=0;p+8<=b.length;p+=8) if(readU64(b,p)==sctmVtable)
                println("SCTM_QWORD_REF|block="+block.getName()+"|address="+block.getStart().add(p));
        }
    }
}
