import ghidra.app.script.GhidraScript;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.mem.MemoryBlock;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FindPhysicsRefs extends GhidraScript {
    private static final class FuncRange {
        final long start;
        final long end;
        FuncRange(long start, long end) { this.start = start; this.end = end; }
    }
    private static final class Hit {
        final FuncRange range;
        final Set<String> strings = new LinkedHashSet<>();
        final List<String> refs = new ArrayList<>();
        Hit(FuncRange range) { this.range = range; }
    }
    private static int u(byte b) { return b & 0xff; }
    private static boolean printable(byte b) { int c = u(b); return c >= 0x20 && c <= 0x7e; }
    private static boolean matches(byte[] source, int offset, byte[] needle) {
        if (offset + needle.length > source.length) return false;
        for (int j = 0; j < needle.length; j++) if (source[offset + j] != needle[j]) return false;
        return true;
    }
    private static int readIntLE(byte[] bytes, int at) {
        return (bytes[at] & 0xff) | ((bytes[at + 1] & 0xff) << 8) |
               ((bytes[at + 2] & 0xff) << 16) | ((bytes[at + 3] & 0xff) << 24);
    }
    private static String hex(long value) { return "0x" + Long.toHexString(value); }
    private static FuncRange findRange(List<FuncRange> ranges, long rva) {
        int lo = 0, hi = ranges.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            FuncRange r = ranges.get(mid);
            if (rva < r.start) hi = mid - 1;
            else if (rva >= r.end) lo = mid + 1;
            else return r;
        }
        return null;
    }
    @Override
    public void run() throws Exception {
        Memory mem = currentProgram.getMemory();
        MemoryBlock text = mem.getBlock(".text");
        MemoryBlock rdata = mem.getBlock(".rdata");
        MemoryBlock dataBlock = mem.getBlock(".data");
        MemoryBlock pdata = mem.getBlock(".pdata");
        if (text == null || rdata == null || dataBlock == null || pdata == null) throw new IllegalStateException("Expected PE sections were not loaded");
        byte[] code = new byte[(int) text.getSize()];
        byte[] data = new byte[(int) rdata.getSize()];
        byte[] extraData = new byte[(int) dataBlock.getSize()];
        byte[] unwind = new byte[(int) pdata.getSize()];
        mem.getBytes(text.getStart(), code);
        mem.getBytes(rdata.getStart(), data);
        mem.getBytes(dataBlock.getStart(), extraData);
        mem.getBytes(pdata.getStart(), unwind);
        long imageBase = currentProgram.getImageBase().getOffset();
        long textStart = text.getStart().getOffset();
        long textEnd = text.getEnd().getOffset() + 1;
        long textStartRva = textStart - imageBase;
        long textEndRva = textEnd - imageBase;
        List<FuncRange> ranges = new ArrayList<>();
        for (int i = 0; i + 12 <= unwind.length; i += 12) {
            long begin = Integer.toUnsignedLong(readIntLE(unwind, i));
            long end = Integer.toUnsignedLong(readIntLE(unwind, i + 4));
            if (begin >= textStartRva && end > begin && end <= textEndRva) ranges.add(new FuncRange(begin, end));
        }
        ranges.sort(Comparator.comparingLong(a -> a.start));
        String[] terms = {
            "Pacejka", "Tyre", "tyre", "Tire", "tire", "Suspension", "suspension",
            "Torque", "torque", "Slip", "slip", "Physics", "physics", "Wheel", "wheel",
            "Grip", "grip", "Friction", "friction", "Differential", "differential", "diff",
            "Brake", "brake", "Engine", "engine", "Lateral", "lateral", "Longitudinal",
            "longitudinal", "Chassis", "chassis", "Spring", "spring", "Damper", "damper",
            "damping", "AntiRoll", "anti_roll", "Aero", "aero", "Downforce", "downforce",
            "Force", "force", "Vehicle"
        };
        Map<Long, String> candidates = new LinkedHashMap<>();
        for (MemoryBlock stringBlock : new MemoryBlock[]{rdata, dataBlock}) {
            byte[] stringData = stringBlock == rdata ? data : extraData;
            for (String term : terms) {
                byte[] needle = term.getBytes(StandardCharsets.US_ASCII);
                for (int i = 0; i <= stringData.length - needle.length; i++) {
                    if (!matches(stringData, i, needle)) continue;
                    int start = i;
                    while (start > 0 && printable(stringData[start - 1]) && i - start < 256) start--;
                    int end = i + needle.length;
                    while (end < stringData.length && printable(stringData[end]) && end - start < 512) end++;
                    String value = new String(stringData, start, end - start, StandardCharsets.US_ASCII);
                    candidates.put(stringBlock.getStart().getOffset() + start, value.replace('|', '/').replace('\n', ' '));
                    i += needle.length - 1;
                }
            }
        }
        Map<Long, Hit> hits = new HashMap<>();
        int directRefs = 0;
        for (int i = 0; i + 8 <= code.length; i++) {
            int p = i;
            boolean addressOverride = false;
            while (p < i + 4) {
                int b = u(code[p]);
                if (b == 0x67) addressOverride = true;
                if (b == 0x66 || b == 0x67 || b == 0xf0 || b == 0xf2 || b == 0xf3 || b == 0x2e || b == 0x36 || b == 0x3e || b == 0x26 || b == 0x64 || b == 0x65 || (b >= 0x40 && b <= 0x4f)) p++;
                else break;
            }
            if (addressOverride || p + 6 > code.length) continue;
            int op = u(code[p]);
            int modrmAt;
            int dispAt;
            if (op == 0x0f && p + 7 < code.length) {
                int op2 = u(code[p + 1]);
                if (op2 != 0xb6 && op2 != 0xb7 && op2 != 0xbe && op2 != 0xbf && op2 != 0x10 && op2 != 0x11 && op2 != 0x28 && op2 != 0x29 && op2 != 0x2e && op2 != 0x2f) continue;
                modrmAt = p + 2;
                dispAt = p + 3;
            } else {
                if (op != 0x8d && op != 0x8b && op != 0x8a && op != 0x89 && op != 0x39 && op != 0x3b && op != 0x38 && op != 0x3a && op != 0x85 && op != 0x84 && op != 0x63) continue;
                modrmAt = p + 1;
                dispAt = p + 2;
            }
            if ((u(code[modrmAt]) & 0xc7) != 0x05 || dispAt + 4 > code.length) continue;
            int disp = readIntLE(code, dispAt);
            long sourceVa = textStart + i;
            long targetVa = sourceVa + (dispAt + 4 - i) + disp;
            String value = candidates.get(targetVa);
            if (value == null) continue;
            long sourceRva = sourceVa - imageBase;
            FuncRange function = findRange(ranges, sourceRva);
            if (function == null) continue;
            Hit hit = hits.computeIfAbsent(function.start, k -> new Hit(function));
            hit.strings.add(value);
            hit.refs.add(hex(sourceVa) + " -> " + hex(targetVa) + " | " + value);
            directRefs++;
            i += (dispAt + 4 - i) - 1;
        }
        List<Hit> ordered = new ArrayList<>(hits.values());
        ordered.sort((a, b) -> {
            int byTerms = Integer.compare(b.strings.size(), a.strings.size());
            if (byTerms != 0) return byTerms;
            return Integer.compare(b.refs.size(), a.refs.size());
        });
        println("SUMMARY|text_bytes=" + code.length + "|pdata_ranges=" + ranges.size() + "|candidate_strings=" + candidates.size() + "|direct_refs=" + directRefs + "|functions=" + ordered.size());
        int shown = 0;
        for (Hit h : ordered) {
            long startVa = imageBase + h.range.start;
            long endVa = imageBase + h.range.end;
            println("FUNC|" + hex(startVa) + "|" + hex(endVa) + "|bytes=" + (h.range.end - h.range.start) + "|distinct_strings=" + h.strings.size() + "|refs=" + h.refs.size());
            for (String ref : h.refs) println("REF|" + ref);
            if (++shown >= 2500) break;
        }
    }
}
