package edu.jhuapl.sd.sig.mmtc.webapp.config;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfigWithTlmSource;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Executable;
import java.util.concurrent.Callable;

public class MmtcWebAppConfig extends MmtcConfigWithTlmSource {
    private static final Logger logger = LogManager.getLogger();

    private final Object spiceLoadedKernelMutex = new Object();

    public MmtcWebAppConfig() throws Exception {
        super();
        logger.debug(toString());
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

    public <T> T withSpiceMutex(Callable<T> callable) throws MmtcException {
        synchronized (spiceLoadedKernelMutex) {
            try {
                TimeConvert.loadSpiceLib();
                return callable.call();
            } catch (Exception e) {
                throw new MmtcException(e);
            } finally {
                TimeConvert.unloadSpiceKernels();
            }
        }
    }

    public <T> T withSpiceMutexAndDefaultKernels(Callable<T> callable) throws MmtcException {
        synchronized (spiceLoadedKernelMutex) {
            try {
                TimeConvert.loadSpiceLib();
                TimeConvert.loadSpiceKernels(getKernelsToLoad());
                return callable.call();
            } catch (Exception e) {
                throw new MmtcException(e);
            } finally {
                TimeConvert.unloadSpiceKernels();
            }
        }
    }
}
