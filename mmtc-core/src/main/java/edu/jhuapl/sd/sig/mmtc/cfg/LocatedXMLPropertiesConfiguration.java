package edu.jhuapl.sd.sig.mmtc.cfg;

import java.net.URL;
import org.apache.commons.configuration2.XMLPropertiesConfiguration;
import org.apache.commons.configuration2.io.FileLocator;

public class LocatedXMLPropertiesConfiguration extends XMLPropertiesConfiguration {
    private URL sourceURL;

    /**
     * Returns the URL intercepted from the FileLocator that was passed on to the superclass.
     *
     * @return the String representation of the URL
     */
    public String getURLString() {
        return this.sourceURL.toString();
    }

    /**
     * Intercepts the FileLocator's source URL before delegating back to the superclass.
     *
     * @param locator the associated FileLocator
     */
    @Override
    public void initFileLocator(final FileLocator locator) {
        this.sourceURL = locator.getSourceURL();
        super.initFileLocator(locator);
    }
}