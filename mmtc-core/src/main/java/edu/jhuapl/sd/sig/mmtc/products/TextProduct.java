package edu.jhuapl.sd.sig.mmtc.products;

import edu.jhuapl.sd.sig.mmtc.app.TimeCorrelationApp;
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
import java.util.stream.Stream;

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

    /* The contents of the original product and an iterator. */
    protected List<String> sourceProductLines;
    protected ListIterator<String> spItr;

    /* Set to true when the sourceProduct has been successfully read in. */
    protected boolean sourceProductReadIn = false;

    /* The contents of the new product to be written and an iterator. */
    protected List<String> newProductLines;
    protected ListIterator<String> npItr;

    /* The time that the product was created. */
    private OffsetDateTime productCreationTimeUtc;


    /**
     * Class constructor. Initializes class attributes.
     */
    public TextProduct() {
        sourceProductLines = new ArrayList<>();
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
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-D'T'HH:mm:ss.SSS");
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
     * Gets the last data record in the original source product.
     *
     * @return the last data record
     */
    public String getLastSourceProdDataRec() {
        return sourceProductLines.get(lastDataRecNum(sourceProductLines)).trim();
    }


    /**
     * Creates a new product file from an existing source file and writes it to the directory
     * and name specified with the current time in UTC as the product creation time. This is
     * the top-level method in this class and the one that would be called from an external
     * method.
     *
     * @throws TextProductException if the file cannot be created
     * @throws TimeConvertException if an error occurred in a computation
     */
    public void createFile() throws TextProductException, TimeConvertException {
        createFile(sourceFilespec, dirname, filename);
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
     */
    public void createFile(String sourceFilespec, String dirname, String filename)
            throws TextProductException, TimeConvertException {

        this.sourceFilespec = sourceFilespec;
        this.dirname        = dirname;
        this.filename       = filename;

        try {
            /* Read the source file */
            readSourceProduct();
            createNewProduct();   /* <-- Abstract method defined in derived class. */
            writeNewProduct();

        } catch (IOException e) {
            throw new TextProductException("Unable to read source product \"" + sourceFilespec + "\".", e);
        }
    }


    /**
     * Reads the product file and place its contents into the local sourceProduct buffer.
     *
     * @throws FileNotFoundException if the source product file could not be found
     * @throws IOException if the source product file could not be read
     * @throws TextProductException if there is a problem with the content of the source product
     */
    public void readSourceProduct() throws FileNotFoundException, IOException, TextProductException {

        String line;

        BufferedReader productReader = new BufferedReader(new FileReader(sourceFilespec));

        while ((line = productReader.readLine()) != null) {
            sourceProductLines.add(line);
        }

        productReader.close();
        sourceProductReadIn = true;
        spItr               = sourceProductLines.listIterator();
    }


    /**
     * Returns the record in the soureceProduct at the specified index.
     * @param recNum IN the 0-based index of the record to return
     * @return the selected record
     */
    public String getSourceProdRecord(int recNum) {
        return sourceProductLines.get(recNum);
    }


    /**
     * Finds a field in the newProduct based upon the supplied pattern, extracts the record containing that
     * field and subsitutes a new value for that field leaving the pattern intact in the record. It then replaces
     * the corresponding record in the new product with that record. A pattern could be something like
     * "FILENAME =" or "CREATION_DATE =". The search is case-sensitive. In the above, the delimeter is "=" and the
     * new field would overwrite the number of characters indicated in fieldLength.
     *
     * @param pattern      IN  the pattern to match that identifies the field
     * @param newFieldVal  IN  the new field value
     * @param delimeter    IN  the delimeter beyond which the new field is to be written
     * @throws TextProductException if the replacement could not be performed
     */
    public void replaceFieldValue(String pattern, String newFieldVal, String delimeter)
            throws TextProductException {

        // ************ Rework this method ***********
        int       recidx;
        String    record;

        recidx = findInProduct(pattern, newProductLines);
        record = newProductLines.get(recidx);

        if (recidx > -1) {
            String fields[]  = record.split(delimeter);
            String newRecord = newFieldVal.trim();
            newProductLines.set(recidx, newRecord);
        }
        else {
                throw new TextProductException("Delmeter " + "\"" + delimeter + "\"" +
                        " not found in record \"" + record + "\".");
        }
    }


    /**
     * Finds a specified pattern in a supplied List of Strings. A pattern could be something like
     * "FILENAME =" or "CREATION_DATE =". The search is case-sensitive and whitespace in the
     * pattern is ignored. Returns as soon as the first occurrence of the pattern is found.
     * Returns -1 if the pattern was never found.
     *
     * @param pattern IN the pattern to search for
     * @param product IN the product to search
     * @return the index in the product (zero-based) of the record containing the pattern
     */
    public static int findInProduct(String pattern, List<String> product) {

        String[]  patterns     = pattern.trim().split("\\s+");
        boolean[] patternFound = new boolean[patterns.length];
        boolean   fieldFound   = true;
        int       rec          = 0;
        String    record       = "";
        int       index        = -1;

        /**
          * Iterate through the sourceProduct list and find the record containing the pattern. The pattern may
          * consist of multiple items separated by whitespace. Whitespace is ignored. Indicate if the pattern
          * is found. Only the first record with a matching pattern is identified.
          */
        for (int i=0; i<product.size(); i++) {
            rec = i;
            record = product.get(i);
            Arrays.fill(patternFound, false);

            /* Initialize to true for AND logic even thourh the field hasn't been forund yet. */
            fieldFound = true;

            for (int j=0; j<patterns.length; j++) {
                if (record.contains(patterns[j])) {
                    patternFound[j] = true;
                }
            }

            /* See if every part of the pattern has been matched in the record. */
            for (int k=0; k<patternFound.length; k++) {
                fieldFound = fieldFound && patternFound[k];
            }
            if (fieldFound) {
                index = rec;
                break;
            }
        }

        return index;
    }


    /**
     * Writes the new product to a file.
     *
     * @throws TextProductException if the file could not be written to.
     */
    protected void writeNewProduct() throws TextProductException {

        String newFilePath = dirname + pathSep + filename;
        Path newFile       = Paths.get(newFilePath);

        if (Files.exists(newFile)) {
            throw new TextProductException("Error writing product file \"" + newFilePath + "\". It already exists.");
        }

        try {
            Files.write(newFile, newProductLines);
        } catch (IOException e) {
            throw new TextProductException("Error creating new Time Correlation file \"" + newFilePath + "\".", e);
        }

        logger.info(TimeCorrelationApp.USER_NOTICE, "Created new time correlation product file: " + newFilePath);
    }


    /**
     * Break a record into its constituent fields. Fields are assumed to be separated by whitespace.
     *
     * @param record    IN the record to parse
     * @param numFields IN the expected number of fields in the record
     * @return the constituent fields of the record
     * @throws TextProductException if the number of fields in the record does not match the expected number
     */
    protected static String[] parseRecord(String record, Integer numFields) throws TextProductException {

        /* Split the record into its constituent fields. */
        String[] fields = record.trim().split("\\s+");
        if (fields.length != numFields) {
            throw new TextProductException("Invalid time correlation record.");
        }

        return fields;
    }

    /**
     * Finds the index (zero-based) of the first data record in the product. The first data record is
     * not necessarily the first file record in a product. This method searches the selected array list
     * and stops as soon as it finds a data record returning the index of that record. Since the form
     * of a data record is product-specific, it calls the abstract method isDataRecord() implemented in
     * a product-specific derived class to identify a data record.
     *
     * @param product     IN the product data to search
     * @return the index of the last data record
     */
    protected int firstDataRecNum( List<String> product) {
        int firstDataRecNum = -1;

        for (int i = 0; i < product.size(); i++) {
            String record = product.get(i);
            if (isDataRecord(record)) {
                firstDataRecNum = i;
                break;
            }
        }

        return firstDataRecNum;
    }

    /**
     * Returns the number of data records contained in the source product's lines of text.
     *
     * @return a non-negative integer representing the found number of data records
     */
    public int getSourceProductDataRecCount() {
        return (int) this.sourceProductLines.stream()
                .filter(this::isDataRecord)
                .count();
    }


    /**
     * Finds the index (zero-based) of the last data record in the product. The last data record is
     * not necessarily the last record in a product. This method searches the selected array list
     * in reverse beginning at the end and stops as soon as it finds a data record returning the index
     * of that record. Since the form of a data record is product-specific, it calls the abstract method
     * isDataRecord() implemented in a product-specific derived class to identify a data record.
     *
     * @param productLines     IN the product data to search
     * @return the index of the last data record
     */
    protected int lastDataRecNum(List<String> productLines) {
        int lastDataRecNum = -1;

        /* Iterate backwards through the product beginning at the end. */
        for (int i=productLines.size()-1; i>0; i--) {

            String record = productLines.get(i);
            if (isDataRecord(record)) {
                lastDataRecNum = i;
                break;
            }
        }

        return lastDataRecNum;
    }


    /**
     * This abstract method creates a new product from the source product. It may perform other operations.
     * This method must be implemented in a derived class.
     *
     * @throws TextProductException if the product could not be created
     * @throws TimeConvertException if an error occurred during a computation
     */
    protected abstract void createNewProduct() throws TextProductException, TimeConvertException;


    /**
     * Indicates if the provided record is a data record as opposed to structural text, labeling or commentary.
     * Since this is product-specific, this method's implementation is left to the derived class.
     *
     * @param record the record to evaluate
     * @return true if the record is a data record, false otherwise
     */
    public abstract boolean isDataRecord(String record);
}
