package edu.jhuapl.sd.sig.mmtc.util;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public static Path findUniqueJarFileWithinDir(Path containingDir, String jarPrefix) throws IOException, MmtcException {
        try (Stream<Path> files = Files.list(containingDir)) {
            List<Path> results = files.filter(p -> p.toString().endsWith(".jar"))
                    .filter(p -> p.getFileName().toString().startsWith(jarPrefix))
                    .collect(Collectors.toList());

            if (results.size() != 1) {
                throw new MmtcException(String.format("A unique plugin jar was not found at the given location: %d matching jars found at %s", results.size(), containingDir));
            }

            return results.get(0);
        }
    }

    public static Long countNumLinesInFile(Path path) throws IOException {
        try (Stream<String> lineStream = Files.lines(path)) {
            return lineStream.count();
        }
    }

    public static Integer truncateLinesTo(Path pathToProduct, int linesRemainingAfterTruncation) throws IOException {
        final List<String> allOriginalLines = Files.readAllLines(pathToProduct);
        final List<String> linesToKeep = allOriginalLines.subList(0, linesRemainingAfterTruncation);

        // all MMTC output products that are appended to currently end with a newline, which is POSIX convention
        Files.write(
                pathToProduct,
                (String.join("\n", linesToKeep) + "\n").getBytes(StandardCharsets.UTF_8)
        );

        return allOriginalLines.size() - linesToKeep.size();
    }

    public static String readResourceToString(String resourceName) throws IOException {
        return IOUtils.toString(FileUtils.class.getResourceAsStream(resourceName), Charset.defaultCharset());
    }
}
