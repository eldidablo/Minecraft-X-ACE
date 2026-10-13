import ghidra.app.script.GhidraScript;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.mem.MemoryBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FindVtableCodeRefs extends GhidraScript {
    private static final class Range { long start,end; Range(long s,long e){start=s;end=e;} }
    private static final class Hit { Range range; Set<String> refs=new LinkedHashSet<>(); Hit(Range r){range=r;} }
    private static int u(byte b){return b&0xff;}
    private static int readU32(byte[] b,int p){return (b[p]&0xff)|((b[p+1]&0xff)<<8)|((b[p+2]&0xff)<<16)|((b[p+3]&0xff)<<24);}
    private static boolean match(byte[] b,int p,byte[] n){if(p<0||p+n.length>b.length)return false;for(int i=0;i<n.length;i++)if(b[p+i]!=n[i])return false;return true;}
    private static String hex(long v){return "0x"+Long.toHexString(v);}
    private Range findRange(List<Range> ranges,long rva){int lo=0,hi=ranges.size()-1;while(lo<=hi){int m=(lo+hi)>>>1;Range r=ranges.get(m);if(rva<r.start)hi=m-1;else if(rva>=r.end)lo=m+1;else return r;}return null;}
    @Override public void run() throws Exception {
        Memory mem=currentProgram.getMemory();
        MemoryBlock text=mem.getBlock(".text"), pdata=mem.getBlock(".pdata");
        byte[] code=new byte[(int)text.getSize()], unwind=new byte[(int)pdata.getSize()];
        mem.getBytes(text.getStart(),code); mem.getBytes(pdata.getStart(),unwind);
        long imageBase=currentProgram.getImageBase().getOffset(), textVA=text.getStart().getOffset();
        long textStartRva=textVA-imageBase, textEndRva=text.getEnd().getOffset()+1-imageBase;
        List<Range> ranges=new ArrayList<>();
        for(int p=0;p+12<=unwind.length;p+=12){long a=Integer.toUnsignedLong(readU32(unwind,p)),b=Integer.toUnsignedLong(readU32(unwind,p+4));if(a>=textStartRva&&b>a&&b<=textEndRva)ranges.add(new Range(a,b));}
        ranges.sort(Comparator.comparingLong(r->r.start));
        Map<Long,String> targets=new LinkedHashMap<>();
        targets.put(0x1455e4250L,"Car vtable");
        targets.put(0x1455fb1c8L,"Suspension vtable");
        targets.put(0x1455fb908L,"SuspensionML vtable");
        targets.put(0x1455fb6a8L,"SuspensionStrut vtable");
        targets.put(0x1455fc4c8L,"SuspensionWishboneCoil vtable");
        targets.put(0x1455fc048L,"SuspensionTrailingArm vtable");
        targets.put(0x145606230L,"PhysicsCore vtable");
        targets.put(0x1456065d8L,"PhysicsCoreExperimental vtable");
        targets.put(0x1456057d8L,"SCTM concrete tyre-model vtable");
        Map<Long,Hit> hits=new LinkedHashMap<>();
        for(int i=0;i+8<=code.length;i++){
            int p=i; boolean addrOverride=false;
            while(p<i+4){int b=u(code[p]);if(b==0x67)addrOverride=true;if(b==0x66||b==0x67||b==0xf0||b==0xf2||b==0xf3||b==0x2e||b==0x36||b==0x3e||b==0x26||b==0x64||b==0x65||(b>=0x40&&b<=0x4f))p++;else break;}
            if(addrOverride||p+6>code.length)continue;
            int op=u(code[p]),modrmAt,dispAt;
            if(op==0x0f&&p+7<code.length){int op2=u(code[p+1]);if(op2!=0xb6&&op2!=0xb7&&op2!=0xbe&&op2!=0xbf&&op2!=0x10&&op2!=0x11&&op2!=0x28&&op2!=0x29&&op2!=0x2e&&op2!=0x2f)continue;modrmAt=p+2;dispAt=p+3;}
            else{if(op!=0x8d&&op!=0x8b&&op!=0x8a&&op!=0x89&&op!=0x39&&op!=0x3b&&op!=0x38&&op!=0x3a&&op!=0x85&&op!=0x84&&op!=0x63)continue;modrmAt=p+1;dispAt=p+2;}
            if((u(code[modrmAt])&0xc7)!=0x05||dispAt+4>code.length)continue;
            int disp=readU32(code,dispAt);long src=textVA+i;long dest=src+(dispAt+4-i)+disp;String label=targets.get(dest);if(label==null)continue;
            Range r=findRange(ranges,src-imageBase);if(r==null)continue;
            Hit h=hits.computeIfAbsent(r.start,k->new Hit(r));h.refs.add(hex(src)+" -> "+hex(dest)+" | "+label);
            i+=(dispAt+4-i)-1;
        }
        List<Hit> ordered=new ArrayList<>(hits.values());ordered.sort((a,b)->Integer.compare(b.refs.size(),a.refs.size()));
        for(Hit h:ordered){println("FUNC|"+hex(imageBase+h.range.start)+"|"+hex(imageBase+h.range.end-1)+"|bytes="+(h.range.end-h.range.start)+"|refs="+h.refs.size());for(String s:h.refs)println("REF|"+s);}
    }
}
