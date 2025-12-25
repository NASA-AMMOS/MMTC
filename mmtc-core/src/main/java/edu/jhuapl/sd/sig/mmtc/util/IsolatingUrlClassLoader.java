package edu.jhuapl.sd.sig.mmtc.util;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This classloader makes the following changes compared to "normal" or built-in classloaders:
 * 
 * <ul>
 * 
 * <li>It prioritizes searching locally for classes instead of delegating to parent classloaders.
 * This has the effect of allowing plugins to safely bundle their own dependencies without regard
 * to whether or not MMTC also depends on the same packages. Plugin code can rely on the specific
 * versions of dependencies that are bundled with the plugin rather than having conflicts if MMTC
 * itself happens to depend on different versions of those dependencies.</li>
 * 
 * <li>It delegates to the parent classloader (i.e. the classloader that loaded MMTC itself) if and
 * only if the requested class is in MMTC's package namespace (edu.jhuapl.sd.sig.mmtc and
 * subpackages). Otherwise, it delegates only to the Java Extension and System Classloaders. This has the
 * effect of allowing plugins to depend on classes exposed by mmtc-core (classes that we wish to
 * expose to plugins in order to provide an API or MMTC convenience functionality) as well as any
 * built-in Java and javax classes, while preventing plugins from accidentally or intentionally
 * relying transitively on MMTC's own dependencies. This has the effect of requiring plugins to
 * bundle their own dependencies.</li>
 * 
 * </ul>
 */
public class IsolatingUrlClassLoader extends URLClassLoader {
    // Java extensions (and this classloader) were removed in Java 9+; remove this if/when MMTC is migrated to Java 9+
    private static final ClassLoader EXTENSION_CLASSLOADER = ClassLoader.getSystemClassLoader().getParent();

    // These are libraries that mmtc-core uses that are valuable to expose to plugins, via the original classloader, to allow plugins
    // to interface with the mmtc-core code (while avoiding loading two different copies of the same class, which prevents the types from being treated as identical)
    // e.g., this allows us to handle the fact that the TelemetrySource interface uses Commons CLI's Option class, so we need to load it once and then share
    private static final List<String> THIRD_PARTY_PACKAGES_TO_ALLOW_PASSTHROUGH = Arrays.asList("org.apache.commons.cli", "com.google.common.collect");

    private static final Logger logger = LogManager.getLogger();

    public IsolatingUrlClassLoader(URL[] urls) {
        super(urls);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        log(name, "start");

        // First check whether it's already been loaded, if so use it
        Class loadedClass = findLoadedClass(name);
        log(name, String.format("did findLoadedClass find it? %b", loadedClass != null));

        // if not loaded, try to load it
        if (loadedClass == null) {

            if (! allowThirdPartyPassthrough(name)) {
                try {
                    // Ignore parent delegation and just try to load locally
                    loadedClass = findClass(name);
                    log(name, String.format("findClass found it"));
                } catch (ClassNotFoundException e) {
                    // Swallow exception - does not exist locally
                    log(name, String.format("findClass did not find it"));
                }
            }

            // If not found locally, delegate to an appropriate classloader
            if (loadedClass == null) {
                if (name.startsWith("edu.jhuapl.sd.sig.mmtc") || allowThirdPartyPassthrough(name)) {
                    // If requesting an MMTC class (e.g. API or utility class), delegate to super.loadClass, which can access MMTC packages
                    log(name, String.format("calling super.loadClass"));
                    // the following call throws ClassNotFoundException if not found in delegation hierarchy at all
                    loadedClass = super.loadClass(name);
                    log(name, String.format("call to super.loadClass succeeded"));
                } else {
                    // For all other classes, delegate to the extension and bootstrap classloaders, which can only access "native" java/javax/etc. packages
                    try {
                        log(name, "delegating to extension classloader");
                        loadedClass = EXTENSION_CLASSLOADER.loadClass(name);
                    } catch (ClassNotFoundException e) {
                        log(name, "delegating to bootstrap classloader");
                        loadedClass = Class.forName(name, true, null);
                    }
                }
            }
        }

        // will never return null (ClassNotFoundException will be thrown)
        return loadedClass;
    }

    private static void log(String className, String message) {
        logger.trace(String.format("IsolatingClassLoader: [%s]\t%s\t\t%s", Thread.currentThread().getId(), className, message));
    }

    private static boolean allowThirdPartyPassthrough(String classname) {
        for (String allowedPackage : THIRD_PARTY_PACKAGES_TO_ALLOW_PASSTHROUGH) {
            if (classname.startsWith(allowedPackage)) {
                return true;
            }
        }

        return false;
    }
}
