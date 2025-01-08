package edu.jhuapl.sd.sig.mmtc.rollback;

import edu.jhuapl.sd.sig.mmtc.table.AbstractTimeCorrelationTable;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;

/**
 * Generic class for MMTC output products for use in rollback.
 */
class OutputProduct {

    private final String nameInRunHistory;
    private URI uri;
    private int newLatestVal;
    private AbstractTimeCorrelationTable table;
    private final Path path;
    private final File file;
    private String baseName;


    public OutputProduct(String name, URI uri) {
        this.nameInRunHistory = name;
        this.uri = uri;
        this.file = new File(uri);
        this.path = this.file.toPath();
    }

    public OutputProduct(String name, Path path, String basename) {
        this.nameInRunHistory = name;
        this.path = path;
        this.file = new File(path.toString());
        this.baseName = basename;
    }

    public int getNewLatestVal() {
        return newLatestVal;
    }

    public void setNewLatestVal(String newLatestVal) {
        try {
            this.newLatestVal = Integer.parseInt(newLatestVal);
        } catch (NumberFormatException e) {
            this.newLatestVal = 0;
        }
    }

    public File getFile() {
        return file;
    }

    public Path getPath() {
        return path;
    }

    public String getBaseName() {
        return baseName;
    }

    public String getNameInRunHistory() {
        return nameInRunHistory;
    }

    public URI getUri() {
        return uri;
    }

    public AbstractTimeCorrelationTable getTable() {
        return table;
    }

    public void setTable(AbstractTimeCorrelationTable table) {
        this.table = table;
    }

    public int calcLinesToTruncate() {
        return table.getLastLineNumber() - newLatestVal;
    }
}
