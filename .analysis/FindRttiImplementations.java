import ghidra.app.script.GhidraScript;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.mem.MemoryBlock;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FindRttiImplementations extends GhidraScript {
    private static final String[] BASE_TYPES = {
        ".?AVITyreModel@@", ".?AVICarUpdateProcessor@@", ".?AVIPhysicsCore@@", ".?AVIPhysicsEngineController@@",
        ".?AVSuspension@@", ".?AVPhysicsCore@@", ".?AVPhysicsEngine@@"
    };
    private static int u(byte b){return b&0xff;}
    private static long readU32(byte[] b,int p){return ((long)u(b[p]))|((long)u(b[p+1])<<8)|((long)u(b[p+2])<<16)|((long)u(b[p+3])<<24);}
    private static long readU64(byte[] b,int p){long v=0;for(int i=7;i>=0;i--)v=(v<<8)|u(b[p+i]);return v;}
    private static boolean match(byte[] b,int p,byte[] n){if(p<0||p+n.length>b.length)return false;for(int i=0;i<n.length;i++)if(b[p+i]!=n[i])return false;return true;}
    private static String hex(long v){return "0x"+Long.toHexString(v);}
    private long readU32At(Memory mem,long va)throws Exception{byte[] b=new byte[4];mem.getBytes(toAddr(hex(va)),b);return readU32(b,0);}
    private String readString(Memory mem,long va)throws Exception{byte[] b=new byte[256];mem.getBytes(toAddr(hex(va)),b);int n=0;while(n<b.length&&b[n]!=0)n++;return new String(b,0,n,StandardCharsets.US_ASCII);}
    @Override public void run() throws Exception {
        Memory mem=currentProgram.getMemory();
        MemoryBlock[] blocks={mem.getBlock(".rdata"),mem.getBlock(".data")};
        long imageBase=currentProgram.getImageBase().getOffset();
        long textStart=mem.getBlock(".text").getStart().getOffset(),textEnd=mem.getBlock(".text").getEnd().getOffset()+1;
        List<Long> baseDescriptorRvas=new ArrayList<>();
        for(String type:BASE_TYPES){
            byte[] needle=type.getBytes(StandardCharsets.US_ASCII);long descriptor=0;
            for(MemoryBlock block:blocks){byte[] bytes=new byte[(int)block.getSize()];mem.getBytes(block.getStart(),bytes);for(int i=16;i+needle.length<=bytes.length;i++)if(match(bytes,i,needle)&&(i+needle.length==bytes.length||bytes[i+needle.length]==0)){descriptor=block.getStart().getOffset()+i-16;break;}if(descriptor!=0)break;}
            if(descriptor==0){println("BASE_TYPE_NOT_FOUND|"+type);continue;}
            long baseRva=descriptor-imageBase;baseDescriptorRvas.add(baseRva);println("BASE_TYPE|"+type+"|descriptor="+hex(descriptor));
            for(MemoryBlock scanBlock:blocks){
                byte[] bytes=new byte[(int)scanBlock.getSize()];mem.getBytes(scanBlock.getStart(),bytes);long blockVA=scanBlock.getStart().getOffset();
                for(int p=0;p+24<=bytes.length;p+=4){
                    long colVA=blockVA+p;long sig=readU32(bytes,p),hierRva=readU32(bytes,p+16),selfRva=readU32(bytes,p+20);
                    if(sig!=1||selfRva!=colVA-imageBase)continue;
                    long derivedTypeRva=readU32(bytes,p+12);
                    long chdVA=imageBase+hierRva;long baseCount=readU32At(mem,chdVA+8);long arrayRva=readU32At(mem,chdVA+12);
                    if(baseCount<1||baseCount>256)continue;
                    boolean implementsBase=false;
                    for(int j=0;j<baseCount;j++){
                        long bcdRva=readU32At(mem,imageBase+arrayRva+4L*j);
                        long tdRva=readU32At(mem,imageBase+bcdRva);
                        if(tdRva==baseRva){implementsBase=true;break;}
                    }
                    if(!implementsBase)continue;
                    long derivedTd=imageBase+derivedTypeRva;String derivedName=readString(mem,derivedTd+16);
                    println("IMPLEMENTATION|base="+type+"|derived="+derivedName+"|col="+hex(colVA)+"|typeDescriptor="+hex(derivedTd)+"|baseCount="+baseCount);
                    byte[] colPointer=new byte[8];for(int j=0;j<8;j++)colPointer[j]=(byte)(colVA>>>(8*j));
                    for(MemoryBlock refBlock:blocks){byte[] refBytes=new byte[(int)refBlock.getSize()];mem.getBytes(refBlock.getStart(),refBytes);for(int q=0;q+16<=refBytes.length;q+=8){if(!match(refBytes,q,colPointer))continue;long vt=refBlock.getStart().getOffset()+q+8;println("VTABLE_START|"+derivedName+"|"+hex(vt));for(int k=0;k<80&&q+8+8*k+8<=refBytes.length;k++){long fn=readU64(refBytes,q+8+8*k);if(fn<textStart||fn>=textEnd)break;println("VTABLE|"+derivedName+"|slot="+k+"|fn="+hex(fn));}}}
                }
            }
        }
    }
}
