import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressSet;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.listing.InstructionIterator;

public class InspectFunctionEvidence extends GhidraScript {
    private static final String[][] TARGETS = {
        {"0x1423d88d0", "0x1423dad8b", "0x1423d95b7", "0x1423d99b3"},
        {"0x142372970", "0x142373c14", "0x14237359f", "0x14237384c"},
        {"0x142b60e80", "0x142b611b4", "0x142b610eb", "0x142b60eb4"},
        {"0x142b5eac0", "0x142b5ef13", "0x142b5ee44", "0x142b5ebde"}
    };
    @Override
    public void run() throws Exception {
        for (String[] t : TARGETS) {
            Address start = toAddr(t[0]);
            Address end = toAddr(t[1]);
            AddressSet range = new AddressSet(start, end);
            Function f = currentProgram.getFunctionManager().getFunctionAt(start);
            long count = 0;
            StringBuilder selected = new StringBuilder();
            InstructionIterator instructions = currentProgram.getListing().getInstructions(range, true);
            while (instructions.hasNext()) {
                Instruction i = instructions.next();
                count++;
                if (i.getAddress().compareTo(toAddr(t[2])) >= 0 && i.getAddress().compareTo(toAddr(t[3])) <= 0) {
                    selected.append(i.getAddress()).append(" | ").append(i).append(" | bytes=").append(i.getLength()).append("\n");
                }
            }
            println("FUNC|" + t[0] + "|" + t[1] + "|function=" + (f == null ? "null" : f.getName()) + "|bodyBytes=" + (f == null ? -1 : f.getBody().getNumAddresses()) + "|instructionsInPdataRange=" + count);
            println("INSTRUCTIONS|\n" + selected);
        }
    }
}
