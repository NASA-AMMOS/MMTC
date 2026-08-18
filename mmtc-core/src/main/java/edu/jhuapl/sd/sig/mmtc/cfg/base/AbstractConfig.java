package edu.jhuapl.sd.sig.mmtc.cfg.base;

import java.nio.file.Path;

/**
 * Defines an abstract class for configuration files.
 */
public abstract class AbstractConfig {
    protected Path path;

    /**
     * Create the configuration object with the specified filename.
     *
     * @param path the name of the configuration file
     */
    protected AbstractConfig(final Path path) {
        this.path = path;
    }

    /**
     * Load the contents of the configuration file into memory.
     *
     * @return true if successful, false otherwise
     */
    public abstract boolean load();

    /**
     * Sets the path attribute to the location of the configuration file.
     *
     * @param filepath the full path to the configuration file
     */
    protected void setPath(Path filepath) {
        this.path = filepath;
    }

    /**
     * @return the properties configuration filename
     */
    public Path getPath() {
        return path;
    }
}
