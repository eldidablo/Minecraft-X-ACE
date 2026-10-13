import ghidra.app.script.GhidraScript;
import ghidra.program.model.mem.MemoryBlock;

public class PrintSections extends GhidraScript {
    @Override
    public void run() throws Exception {
        println("PROGRAM|" + currentProgram.getName());
        println("IMAGE_BASE|" + currentProgram.getImageBase());
        for (MemoryBlock block : currentProgram.getMemory().getBlocks()) {
            println("BLOCK|" + block.getName() + "|" + block.getStart() + "|" + block.getEnd() + "|" + block.getSize() + "|initialized=" + block.isInitialized());
        }
    }
}
