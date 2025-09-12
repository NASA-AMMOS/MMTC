package edu.jhuapl.sd.sig.mmtc.products.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;

/**
 * A generic table record wrapper, which is comprised of a mapping between
 * column headers and column values. A linked hash map is used to retain the
 * insert order of the column headers so that the writeRecord() function prints
 * the values in the correct order.
 */
public class TableRecord {
    private LinkedHashMap<String, String> data;

    /**
     * Create the record and set all values to '-'.
     *
     * @param headers the list of column headers (in order)
     */
    public TableRecord(List<String> headers) {
        data = new LinkedHashMap<>();

        for (String header : headers) {
            data.put(header, "-");
        }
    }

    public TableRecord(TableRecord other) {
        data = new LinkedHashMap<>();

        for (Map.Entry<String, String> otherData : other.data.entrySet()) {
            data.put(otherData.getKey(), otherData.getValue());
        }
    }

    /**
     * Get the list of values.
     *
     * @return the list of values as strings.
     */
    public Collection<String> getValues() {
        return data.values();
    }

    /**
     * Get a particular value.
     *
     * @param key the name of the parameter
     * @return the parameter value as a string
     */
    public String getValue(String key) {
        return data.get(key);
    }

    /**
     * Set one or more values. The empty string (""), "NaN", and "-2147483648"
     * (Integer.MIN_VALUE) are assumed to be null values for string, floating point,
     * and integer fields, respectively, so they are replaced by "-". A literal null
     * is also replaced by "-".
     * 
     * @param entries the key-value pairs to be set
     */
    public void setValues(Map<String, String> entries) {
        for (Entry<String, String> entry : entries.entrySet()) {
            setValue(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Set a particular value. The empty string (""), "NaN", and "-2147483648"
     * (Integer.MIN_VALUE) are assumed to be null values for string, floating point,
     * and integer fields, respectively, so they are replaced by "-". A literal null
     * is also replaced by "-".
     * 
     * In the future, if fields can have any other null values (if, for example, we
     * add BigDecimal fields or we decide to use Integer.MAX_VALUE as a null value),
     * then this method must be updated accordingly.
     *
     * @param key   the name of the parameter
     * @param value the value of the parameter as a string
     */
    private void setValueOrEmpty(String key, String value) {
        if (value == null || value.equals("") || value.equals("NaN") || value.equals("-2147483648")) {
            value = "-";
        }
        data.put(key, value);
    }

    public void setValue(String key, Supplier<String> supplier) {
        if (this.data.containsKey(key)) {
            setValueOrEmpty(key, supplier.get());
        }
    }

    public void setValue(String key, String value) {
        if (this.data.containsKey(key)) {
            setValueOrEmpty(key, value);
        }
    }

}
