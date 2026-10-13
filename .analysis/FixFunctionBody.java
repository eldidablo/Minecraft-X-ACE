import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressSet;
import ghidra.program.model.listing.Function;

public class FixFunctionBody extends GhidraScript {
    private static final String[][] TARGETS = {
        {"0x1423d88d0", "0x1423dad8b"},
        {"0x142372970", "0x142373c14"},
        {"0x142b60e80", "0x142b611b4"},
        {"0x142b5eac0", "0x142b5ef13"}
    };
    @Override
    public void run() throws Exception {
        for (String[] target : TARGETS) {
            Address start = toAddr(target[0]);
            Address end = toAddr(target[1]);
            Function function = currentProgram.getFunctionManager().getFunctionAt(start);
            if (function == null) function = createFunction(start, null);
            if (function == null) throw new IllegalStateException("Could not create function at " + start);
            function.setBody(new AddressSet(start, end));
            println("FUNCTION_BODY|" + function.getName() + "|" + start + "|" + end + "|" + function.getBody().getNumAddresses());
        }
    }
}
