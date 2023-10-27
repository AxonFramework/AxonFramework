/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.serialization.xml;

import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.CompactWriter;
import com.thoughtworks.xstream.io.xml.XppDriver;

import java.io.*;
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
