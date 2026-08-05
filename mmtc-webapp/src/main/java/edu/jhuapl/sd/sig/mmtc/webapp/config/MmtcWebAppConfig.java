package edu.jhuapl.sd.sig.mmtc.webapp.config;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfigWithTlmSource;
import edu.jhuapl.sd.sig.mmtc.products.util.BuiltInOutputProductMigrationManager;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class MmtcWebAppConfig extends MmtcConfigWithTlmSource {
    private static final Logger logger = LogManager.getLogger();

    private final Object spiceLoadedKernelMutex = new Object();

    public MmtcWebAppConfig() throws Exception {
        super();
        logger.debug(toString());
        validate();
        this.telemetrySource.applyConfiguration(this);
    }

    public enum AuthMode {
        NONE,
        AUTOGEN_BASIC_HTTP_AUTH
    }

    @Override
    public void validate() throws MmtcException {
        super.validate();
        super.validateMigrationNotNeeded();
    }

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

    public String getTlsKeystoreLocation() {
        return getString("webapp.serve.tls.keystore.location");
    }

    public String getTlsKeystorePassword() {
        return getString("webapp.serve.tls.keystore.password");
    }

    public int getAuthTimeoutHours() {
        return getInt("webapp.auth.timeout", 4);
    }

    public boolean isTestModeOwltEnabled() {
        return getBoolean("webapp.testmode.enabled", false);
    }

    public double getTestModeOwltSec() {
        return getDouble("webapp.testmode.owltSec", 0.0);
    }

    public Path getLeapSecondsKernelPath() { return Paths.get(getString("webapp.leapSecondsKernelPath")); }

    public <T> T withSpiceMutex(Callable<T> callable) throws MmtcException {
        synchronized (spiceLoadedKernelMutex) {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new MmtcException(e);
            } finally {
                TimeConvert.unloadSpiceKernels();
                try {
                    TimeConvert.loadSpiceKernel(getLeapSecondsKernelPath().toString());
                } catch (TimeConvertException e) {
                    throw new MmtcException(e);
                }
            }
        }
    }

    public <T> T withSpiceMutexAndKernels(Path sclkKernelPath, Callable<T> callable) throws MmtcException {
        synchronized (spiceLoadedKernelMutex) {
            try {
                TimeConvert.loadSpiceLib();
                TimeConvert.loadSpiceKernels(getKernelsToLoad(false));
                TimeConvert.loadSpiceKernel(sclkKernelPath.toAbsolutePath().toString());
                TimeConvert.validateLoadedSclkKernels(getSpacecraftId());

                return callable.call();
            } catch (Exception e) {
                throw new MmtcException(e);
            } finally {
                TimeConvert.unloadSpiceKernels();
                try {
                    TimeConvert.loadSpiceKernel(getLeapSecondsKernelPath().toString());
                } catch (TimeConvertException e) {
                    throw new MmtcException(e);
                }
            }
        }
    }
}
