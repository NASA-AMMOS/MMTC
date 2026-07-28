package edu.jhuapl.sd.sig.mmtc.products.model;

import edu.jhuapl.sd.sig.mmtc.app.MmtcCli;
import edu.jhuapl.sd.sig.mmtc.correlation.TimeCorrelationContext;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvertException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.nio.file.*;

/**
 * The TextProduct class is an abstract base class that provides basic functions that derived classes use to
 * create the primary time correlation products.
 */
abstract class TextProduct {

    /* The log file object */
    protected static final Logger logger = LogManager.getLogger();

    /* The name of the new time correlation file */
    protected String filename;

    /* The directory to which the new time correlation file is to be written. */
    protected String dirname;

    /* The full file specification of the original source time correlation file from which the new
     * one is to be created.
     */
    protected String sourceFilespec;

    /**
     * The directory path separator (e.g., forward slash "/" on Unix or Mac, backslash "\" on Windows.
     */
    protected static String pathSep;

    /**
     * The system new line marker. On Unix-type systems this is {@literal <LF>}. On Windows, this
     * is {@literal <CR><LF>}.
     */
    protected static String nl;

    /* The contents of the new product to be written and an iterator. */
    protected List<String> newProductLines;
    protected ListIterator<String> npItr;

    /* The time that the product was created. */
    private OffsetDateTime productCreationTimeUtc;


    /**
     * Class constructor. Initializes class attributes.
     */
    public TextProduct() {
        newProductLines = new ArrayList<>();
        pathSep         = File.separator;
        nl              = System.lineSeparator();
    }

    /**
     * Sets the product creation time.
     *
     * @param utc time in UTC as a Java OffsetDateTime object.
     */
    public void setProductCreationTime(OffsetDateTime utc) {
        this.productCreationTimeUtc = utc;
    }


    /**
     * Returns the product creation time as a Java OffsetDateTime object.
     *
     * @return the product creation time
     */
    public OffsetDateTime getProductCreationTime() {
        return productCreationTimeUtc;
    }


    /**
     * Create a new UTC date/time string for the Product Creation Date in the form of an
     * ISO day of year calendar string.
     *
     * @return the date/time string
     */
    public String getProductDateTimeIsoUtc() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-DDD'T'HH:mm:ss.SSS");
        OffsetDateTime utc          = productCreationTimeUtc;
        String newDateTime          = utc.format(formatter);

        return newDateTime;
    }


    /**
     * Sets the name of the new file to be created.
     *
     * @param filename IN the name of the new file
     */
    public void setName(String filename) {
        this.filename = filename;
    }


    /**
     * Gets the full file specification of the product file.
     *
     * @return the full file specification
     */
    public String getPath() {
        return dirname + pathSep + filename;
    }

    /**
     * Gets the name of the new product file.
     *
     * @return the filename
     */
    public String getName() {
        return filename;
    }


    /**
     * Sets the directory to which the new file is to be created.
     *
     * @param dirname IN the directory path to write to
     */
    public void setDir(String dirname) {
        this.dirname = dirname;
    }


    /**
     * Sets the full path to the original source product file.
     *
     * @param filespec full path to the source product file
     */
    public void setSourceFilespec(String filespec) {
        sourceFilespec = filespec;
    }



    /**
     * Creates a new product file from an existing source file and writes it to the directory
     * and name specified with the current time in UTC as the product creation time. This is
     * the top-level method in this class and the one that would be called from an external
     * method.
     *
     * @throws TextProductException if the file cannot be created
     * @throws TimeConvertException if an error occurred in a computation
     * @return the Path representing the location where the new file was written
     */
    public Path createFile(TimeCorrelationContext ctx) throws TextProductException, TimeConvertException {
        return createFile(ctx, sourceFilespec, dirname, filename);
    }

    /**
     * Creates a new product file from an existing source file and writes it to the directory
     * and name specified with the current time in UTC as the product creation time. This is
     * the top-level method in this class and the one that would be called from an external
     * method. This overload is intended for dry runs where a temporary output dir is to be
     * specified.
     *
     * @throws TextProductException if the file cannot be created
     * @throws TimeConvertException if an error occurred in a computation
     * @return the Path representing the location where the new file was written
     */
    public Path createFile(TimeCorrelationContext ctx, String path) throws TextProductException, TimeConvertException {
        return createFile(ctx, sourceFilespec, path, filename);
    }

    public Path createFile(TimeCorrelationContext ctx, Path path) throws TextProductException, TimeConvertException {
        return createFile(ctx, sourceFilespec, path.getParent().toString(), path.getFileName().toString());
    }

    /**
     * Creates a new product file from an existing source file and writes it to the directory
     * and name specified with the specified time in UTC as the product creation time. This is
     * the top-level method in this class and the one that would be called from an external
     * method.
     *
     * @param sourceFilespec IN the full file specification of the source file
     * @param dirname        IN the directory path to write to
     * @param filename       IN the name of the new file
     * @throws TextProductException if the file cannot be created
     * @throws TimeConvertException if an error occurred during a computation
     * @return the Path representing the location where the new file was written
     */
    public Path createFile(TimeCorrelationContext ctx, String sourceFilespec, String dirname, String filename) throws TextProductException, TimeConvertException {

        this.sourceFilespec = sourceFilespec;
        this.dirname        = dirname;
        this.filename       = filename;

        try {
            /* Read the source file */
            updateFile(ctx);
            return writeNewProduct(ctx);
        } catch (IOException e) {
            throw new TextProductException("Unable to read source product \"" + sourceFilespec + "\".", e);
        }
    }

    public void updateFile(TimeCorrelationContext ctx) throws TextProductException, IOException, TimeConvertException {
        createNewProduct(ctx);   /* <-- Abstract method defined in derived class. */
    }

    /**
     * Writes the new product to a file.
     *
     * @throws TextProductException if the file could not be written to.
     * @return the Path representing the location where the new file was written
     */
    protected Path writeNewProduct(TimeCorrelationContext ctx) throws TextProductException {

        String newFilePath = dirname + pathSep + filename;
        Path newFile       = Paths.get(newFilePath);

        if (Files.exists(newFile)) {
            throw new TextProductException("Error writing product file \"" + newFilePath + "\". It already exists.");
        }

        try {
            if(dirname.equals("/tmp")) {
                File tempKernel = File.createTempFile("temp_sclk_kernel-", "-"+filename);
                newFile = tempKernel.toPath();
                filename = tempKernel.getName();
                tempKernel.deleteOnExit();
            }
            Files.write(newFile, newProductLines);
        } catch (IOException e) {
            throw new TextProductException("Error creating new Time Correlation file \"" + newFilePath + "\".", e);
        }

        if (dirname.equals("/tmp")) {
            logger.debug("Wrote temporary SCLK kernel {} to {} and set to delete on exit", filename, dirname);
        } else {
            logger.info(MmtcCli.USER_NOTICE, "Created new time correlation product file: " + newFilePath);
        }

        return newFile;
    }


    /**
     * This abstract method creates a new product from the source product. It may perform other operations.
     * This method must be implemented in a derived class.
     *
     * @throws TextProductException if the product could not be created
     * @throws TimeConvertException if an error occurred during a computation
     */
    protected abstract void createNewProduct(TimeCorrelationContext ctx) throws TextProductException, TimeConvertException;

}
