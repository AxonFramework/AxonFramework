package org.axonframework.serializer.xml;

import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.CompactWriter;
import com.thoughtworks.xstream.io.xml.XppDriver;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;

/**
 * XStream HierarchicalStreamDriver implementation that uses a CompactWriter to write XML without newlines and
 * indentation, while writing it using the (default) XPPReader.
 * <p/>
 * Note: this implementation does not support writing to an OutputStream, due to potential Character Set issues. Always
 * write to a text based output stream, such as the {@link java.io.OutputStreamWriter}.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CompactDriver implements HierarchicalStreamDriver {

    private final XppDriver xppDriver = new XppDriver();

    @Override
    public HierarchicalStreamReader createReader(Reader in) {
        return xppDriver.createReader(in);
    }

    @Override
    public HierarchicalStreamReader createReader(InputStream in) {
        return xppDriver.createReader(in);
    }

    @Override
    public HierarchicalStreamReader createReader(URL in) {
        return xppDriver.createReader(in);
    }

    @Override
    public HierarchicalStreamReader createReader(File in) {
        return xppDriver.createReader(in);
    }

    @Override
    public HierarchicalStreamWriter createWriter(Writer out) {
        return new CompactWriter(out);
    }

    @Override
    public HierarchicalStreamWriter createWriter(OutputStream out) {
        throw new UnsupportedOperationException("Not supported due to possible character set issues.");
    }
}
