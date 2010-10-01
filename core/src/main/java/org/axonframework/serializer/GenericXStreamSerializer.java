/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.serializer;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.CompactWriter;
import com.thoughtworks.xstream.io.xml.Dom4JReader;
import com.thoughtworks.xstream.io.xml.XppDriver;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.util.SerializationException;
import org.dom4j.Document;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.UUID;

/**
 * Serializer that uses XStream to serialize and deserialize arbitrary objects.
 * <p/>
 * When running on a Sun JVM, XStream does not pose any restrictions on classes to serialize. On other JVM's, however,
 * you need to either implement Serializable, or provide a default constructor (accesible under the JVM's security
 * policy). That means that for portability, you should do either of these two.
 *
 * @author Allard Buijze
 * @see com.thoughtworks.xstream.XStream
 * @since 0.6
 */
public class GenericXStreamSerializer {

    private final XStream xStream;
    private final Charset charset;

    /**
     * Initialize a generic serializer using the UTF-8 character set.
     */
    public GenericXStreamSerializer() {
        this(Charset.forName("UTF-8"));
    }

    /**
     * Initialize the serializer using the given <code>charset</code>.
     *
     * @param charset The character set to use
     */
    public GenericXStreamSerializer(Charset charset) {
        this.charset = charset;
        xStream = new XStream(new XppDriver());
        xStream.registerConverter(new JodaTimeConverter());
        xStream.addImmutableType(UUID.class);
        xStream.registerConverter(new AggregateIdentifierConverter());
        xStream.addImmutableType(AggregateIdentifier.class);
        xStream.aliasType("aggregateIdentifier", AggregateIdentifier.class);
        xStream.aliasType("localDateTime", LocalDateTime.class);
        xStream.aliasType("dateTime", DateTime.class);
        xStream.aliasType("uuid", UUID.class);
    }

    /**
     * Serialize the given <code>object</code> and write the bytes to the given <code>outputStream</code>. Bytes are
     * written using the character set provided during initialization of the serializer.
     *
     * @param object       The object to serialize.
     * @param outputStream The stream to write bytes to
     */
    public void serialize(Object object, OutputStream outputStream) {
        xStream.marshal(object, new CompactWriter(new OutputStreamWriter(outputStream, charset)));
    }

    /**
     * Deserialize an object using the bytes in the given inputStream. The deserialization process may read more bytes
     * from the input stream than might be absolutely necessary.
     *
     * @param inputStream The input stream providing the bytes of the serialized object
     * @return the deserialized object
     */
    public Object deserialize(InputStream inputStream) {
        return xStream.fromXML(new InputStreamReader(inputStream, charset));
    }

    /**
     * Deserialize an object using the given dom4j Document. The document needs to describe the XML as it can be parsed
     * by XStream.
     *
     * @param document The dom4j Document describing the event
     * @return the deserialized object
     */
    public Object deserialize(Document document) {
        return xStream.unmarshal(new Dom4JReader(document));
    }

    /**
     * Adds an alias to use instead of the fully qualified class name.
     *
     * @param name The alias to use
     * @param type The Class to use the alias for
     * @see XStream#alias(String, Class)
     */
    public void addAlias(String name, Class type) {
        xStream.alias(name, type);
    }

    /**
     * Add an alias for a package. This allows long package names to be shortened considerably. Will also use the alias
     * for subpackages of the provided package.
     * <p/>
     * E.g. an alias of "axoncore" for the package "org.axonframework.core" will use "axoncore.repository" for the
     * package "org.axonframework.core.repository".
     *
     * @param alias   The alias to use.
     * @param pkgName The package to use the alias for
     * @see XStream#aliasPackage(String, String)
     */
    public void addPackageAlias(String alias, String pkgName) {
        xStream.aliasPackage(alias, pkgName);
    }

    /**
     * Adds an alias to use for a given field in the given class.
     *
     * @param alias     The alias to use instead of the original field name
     * @param definedIn The class that defines the field.
     * @param fieldName The name of the field to use the alias for
     * @see XStream#aliasField(String, Class, String)
     */
    public void addFieldAlias(String alias, Class definedIn, String fieldName) {
        xStream.aliasField(alias, definedIn, fieldName);
    }

    /**
     * Returns a reference to the underlying {@link com.thoughtworks.xstream.XStream} instance, that does the actual
     * serialization.
     *
     * @return the XStream instance that does the actual (de)serialization.
     *
     * @see com.thoughtworks.xstream.XStream
     */
    public XStream getXStream() {
        return xStream;
    }

    /**
     * XStream Converter to serialize LocalDateTime classes as a String.
     */
    private static class JodaTimeConverter implements Converter {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean canConvert(Class type) {
            return type != null && LocalDateTime.class.getPackage().equals(type.getPackage());
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            writer.setValue(source.toString());
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            try {
                Constructor constructor = context.getRequiredType().getConstructor(Object.class);
                return constructor.newInstance(reader.getValue());
            } catch (InvocationTargetException e) {
                throw new SerializationException(buildExceptionMessage(context), e);
            } catch (NoSuchMethodException e) {
                throw new SerializationException(buildExceptionMessage(context), e);
            } catch (InstantiationException e) {
                throw new SerializationException(buildExceptionMessage(context), e);
            } catch (IllegalAccessException e) {
                throw new SerializationException(buildExceptionMessage(context), e);
            }
        }

        private String buildExceptionMessage(UnmarshallingContext context) {
            return String.format(
                    "An exception occurred while deserializing a Joda Time object: %s",
                    context.getRequiredType().getSimpleName());
        }
    }
}
