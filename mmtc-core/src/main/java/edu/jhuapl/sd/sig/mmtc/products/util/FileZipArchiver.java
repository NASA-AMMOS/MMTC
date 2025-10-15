package edu.jhuapl.sd.sig.mmtc.products.util;

import edu.jhuapl.sd.sig.mmtc.app.MmtcException;
import edu.jhuapl.sd.sig.mmtc.cfg.MmtcConfig;
import edu.jhuapl.sd.sig.mmtc.products.definition.OutputProductDefinition;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static edu.jhuapl.sd.sig.mmtc.app.MmtcCli.USER_NOTICE;

// Zips and stores a copy of files to a single zip on the filesystem
public class FileZipArchiver {
    private static final Logger logger = LogManager.getLogger();

    private final Path targetZipFileLocation;
    private final Collection<Path> includedFiles;

    public FileZipArchiver(Path targetZipFileLocation, Collection<Path> includedFiles) throws IOException {
        if (! targetZipFileLocation.toString().endsWith(".zip")) {
            this.targetZipFileLocation = targetZipFileLocation.getParent().resolve(targetZipFileLocation.getFileName().toString() + ".zip");
        } else {
            this.targetZipFileLocation = targetZipFileLocation;
        }

        this.includedFiles = new ArrayList<>(includedFiles);
        Files.createDirectories(targetZipFileLocation.getParent());
    }

    public void write() throws IOException {
        try {
            final FileOutputStream fileOutputStream = new FileOutputStream(this.targetZipFileLocation.toFile());
            final ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);

            for (Path path : includedFiles) {
                // use the absolute & normalized path, but with the leading slash removed so as to give the path relative to root
                ZipEntry zipEntry = new ZipEntry(path.toAbsolutePath().normalize().toString().substring(1));
                zipOutputStream.putNextEntry(zipEntry);

                FileInputStream fileInputStream = new FileInputStream(path.toFile());
                IOUtils.copy(fileInputStream, zipOutputStream);
                fileInputStream.close();
            }

            zipOutputStream.close();
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            throw new IOException(e);
        }
    }

    public static void writeAllOutputProductsToArchive(MmtcConfig config, String archiveNameDescriptor) throws MmtcException, IOException {
        logger.info(USER_NOTICE, "Archiving current output products before continuing...");

        final Set<Path> allFilepathsToBackup = new HashSet<>();
        for (OutputProductDefinition<?> def : config.getAllOutputProductDefs()) {
            if (def.isConfigured(config)) {
                allFilepathsToBackup.addAll(def.resolveAllExistingPaths(config));
            }
        }
        allFilepathsToBackup.add(config.getRunHistoryFilePath().toAbsolutePath());

        final Path archiveOutputFile = config.getProductArchiveLocation().resolve(FileZipArchiver.getArchiveFilename(archiveNameDescriptor));
        new FileZipArchiver(archiveOutputFile, allFilepathsToBackup).write();

        logger.info("Archive written: " + archiveOutputFile.toAbsolutePath().normalize());
    }

    private static String getArchiveFilename(String filenameSuffix) {
        return ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-DDD_HH-mm-ss")) + "_" + filenameSuffix + ".zip";
    }
}
