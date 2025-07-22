package edu.jhuapl.sd.sig.mmtc.cfg;

public class MmtcCliConfig {
    public enum PrimaryApplicationCommand {
        CORRELATION,
        ROLLBACK,
        CREATE_SANDBOX
    }

    public static PrimaryApplicationCommand determineApplicationMode(String... cliArgs) {
        if (cliArgs[0].equalsIgnoreCase("rollback")) {
            return PrimaryApplicationCommand.ROLLBACK;
        } else if (cliArgs[0].equalsIgnoreCase("create-sandbox")) {
            return PrimaryApplicationCommand.CREATE_SANDBOX;
        } else {
            return PrimaryApplicationCommand.CORRELATION;
        }
    }


}
