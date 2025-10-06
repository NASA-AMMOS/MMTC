package edu.jhuapl.sd.sig.mmtc.products.util;

import org.apache.commons.io.IOUtils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ProductArchiver {

    private final Path targetZipFileLocation;
    private final Collection<Path> includedFiles;

    // zips and stores a copy of the products somewhere
    public ProductArchiver(Path targetZipFileLocation, Collection<Path> includedFiles) throws IOException {
        if (! targetZipFileLocation.toString().endsWith(".zip")) {
            this.targetZipFileLocation = targetZipFileLocation.getParent().resolve(targetZipFileLocation.getFileName().toString() + ".zip");
        } else {
            this.targetZipFileLocation = targetZipFileLocation;
        }

        this.includedFiles = new ArrayList<>(includedFiles);
        Files.createDirectories(targetZipFileLocation.getParent());

        // todo either keep directory structure, or ensure each file has a unique name
    }

    public void write() throws IOException {
        try {
            final FileOutputStream fileOutputStream = new FileOutputStream(this.targetZipFileLocation.toFile());
            final ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);

            for (Path path : includedFiles) {
                ZipEntry zipEntry = new ZipEntry(path.getFileName().toString());
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
}
