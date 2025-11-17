package edu.jhuapl.sd.sig.mmtc.webapp;

import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfigWithTlmSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MmtcWebAppConfig extends MmtcConfigWithTlmSource {
    private static final Logger logger = LogManager.getLogger();

    public final Object spiceMutex = new Object();

    public MmtcWebAppConfig() throws Exception {
        super();
        logger.debug(toString());
    }

    @Override
    public String getAdditionalOptionValue(String optionName) {
        return null; // todo should return ampcs connection args and session ID from config here
    }

    public enum AuthMode {
        NONE,
        AUTOGEN_BASIC_HTTP_AUTH
    }

    // todo enable using defaults for these if unspecified
    public AuthMode getAuthMode() {
        return AuthMode.valueOf(getString("webapp.auth.mode").toUpperCase());
    }

    public boolean isPlaintextServerEnabled() {
        return getBoolean("webapp.serve.plaintext.enabled");
    }

    public int getPlaintextServerPort() {
        return getInt("webapp.serve.plaintext.port");
    }

    public boolean isTlsServerEnabled() {
        return getBoolean("webapp.serve.tls.enabled");
    }

    public int getTlsServerPort() {
        return getInt("webapp.serve.tls.port");
    }
}
