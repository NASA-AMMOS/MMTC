package edu.jhuapl.sd.sig.mmtc.util;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtils {
    private static final Logger logger = LogManager.getLogger();

    /**
     * Determines the most recent counter or timestamp of a particular type of output file
     * @param dir the Path of the directory where the relevant files can be found
     * @param prefix the shared basename of the target files, i.e. "uplinkCmd"
     * @param suffix the file type
     * @return the highest numeric suffix of files with the given prefix as a String
     */
    public static String getLatestFileCounterByPrefix(Path dir, String prefix, String suffix) {
        // Create an array of all files in dir that start with prefix
        File directoryPath = new File(dir.toAbsolutePath().toString());
        String[] targetFilenames = directoryPath.list((dir1, name) -> name.startsWith(prefix) && name.endsWith(suffix));

        if (targetFilenames == null || targetFilenames.length == 0) { // No files found (likely haven't been created yet)
            logger.debug(String.format("No files '%s*%s' found in output directory %s, returning '-'.", prefix, suffix, dir));
            return "-";
        }

        // Isolate the counter/date/digit suffix and return the max
        int max = 0;
        for (String file:targetFilenames) {
            int counter = Integer.parseInt(file.replaceAll("^\\D*(\\d+).*$", "$1"));
            if (counter > max) {
                max = counter;
            }
        }

        return String.valueOf(max);
    }

    public static boolean fileExistsAndIsWritable(Path p) {
        return Files.exists(p) && Files.isWritable(p);
    }

    public static String readResourceToString(String resourceName) throws IOException {
        return IOUtils.toString(FileUtils.class.getResourceAsStream(resourceName), Charset.defaultCharset());
    }
}
