package edu.jhuapl.sd.sig.mmtc.app;

import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Properties;

public class BuildInfo {
    public static final String MMTC_TITLE = "Multi-Mission Time Correlation (MMTC)";

    public static final String UNKNOWN_VALUE = "unknown";

    private static final String VERSION_KEY = "version";
    private static final String BUILD_DATE_KEY = "buildDate";
    private static final String COMMIT_KEY = "commit";

    public final String version;
    public final String buildDate;
    public final String commit;

    public BuildInfo() {
        Properties buildProperties = new Properties();

        try {
            buildProperties.load(getClass().getResourceAsStream("/version-description.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        version = buildProperties.getProperty(VERSION_KEY, UNKNOWN_VALUE);
        buildDate = buildProperties.getProperty(BUILD_DATE_KEY, UNKNOWN_VALUE);
        commit = buildProperties.getProperty(COMMIT_KEY, UNKNOWN_VALUE);
    }

    public String getNumericalVersion() {
        return version.replace("-SNAPSHOT", "");
    }

    public String toString() {
        return String.format("Version: %s\nBuild date: %s\nCommit: %s", version, buildDate, commit);
    }

    public static void log(Logger logger) {
        BuildInfo bi = new BuildInfo();
        logger.info(String.format("************ %s version %s ************", MMTC_TITLE, bi.version));
        logger.info(String.format("Commit %s built at %s", bi.commit, bi.buildDate));
    }
}
