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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.xml.Dom4JReader;
import com.thoughtworks.xstream.io.xml.XomReader;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.serialization.AbstractXStreamSerializer;
import org.axonframework.serialization.AnnotationRevisionResolver;
import org.axonframework.serialization.ChainingConverter;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.RevisionResolver;
import org.axonframework.serialization.SerializedObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.invoke.MethodHandles;
import java.nio.charset.Charset;
import javax.annotation.Nonnull;

/**
 * Serializer that uses XStream to serialize and deserialize arbitrary objects. The XStream instance is configured to
 * deal with the Classes used in Axon Framework in the most compact fashion.
 * <p/>
 * When running on a Sun JVM, XStream does not pose any restrictions on classes to serialize. On other JVM's, however,
 * you need to either implement Serializable, or provide a default constructor (accessible under the JVM's security
 * policy). That means that for portability, you should do either of these two.
 *
 * @author Allard Buijze
 * @see com.thoughtworks.xstream.XStream
 * @since 1.2
 */
public class XStreamSerializer extends AbstractXStreamSerializer {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * Instantiate a Builder to be able to create a {@link XStreamSerializer}.
     * <p>
     * The {@link XStream} is defaulted to a {@link XStream#XStream(HierarchicalStreamDriver)} call, providing a {@link
     * CompactDriver}, the {@link Charset} is defaulted to a {@link Charset#forName(String)} using the {@code UTF-8}
     * character set, the {@link RevisionResolver} defaults to an {@link AnnotationRevisionResolver} and the {@link
     * Converter} defaults to a {@link ChainingConverter}. Lastly, the builder adds Axon types for XStream's security
     * settings by including {@code "org.axonframework.**} as a wildcard type. This can be disabled with the {@link
     * Builder#disableAxonTypeSecurity()} operation when required.
     * <p>
     * Upon instantiation, several defaults aliases are added to the XStream instance, for example for the {@link
     * GenericDomainEventMessage}, the {@link org.axonframework.commandhandling.GenericCommandMessage} and the {@link
     * org.axonframework.messaging.MetaData} objects among others. Additionally, a MetaData Converter is registered too.
     * Lastly, if the provided Converter instance is of type ChainingConverter, then the {@link
     * XStreamSerializer#registerConverters(ChainingConverter)} function will be called. This will register the {@link
     * Dom4JToByteArrayConverter}, {@link InputStreamToDom4jConverter}, {@link XomToStringConverter} and {@link
     * InputStreamToXomConverter} to the Converter chain.
     *
     * @return a Builder to be able to create a {@link XStreamSerializer}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a default {@link XStreamSerializer}.
     * <p>
     * The {@link XStream} is defaulted to a {@link XStream#XStream(HierarchicalStreamDriver)} call, providing a {@link
     * CompactDriver}, the {@link Charset} is defaulted to a {@link Charset#forName(String)} using the {@code UTF-8}
     * character set, the {@link RevisionResolver} defaults to an {@link AnnotationRevisionResolver} and the {@link
     * Converter} defaults to a {@link ChainingConverter}. Lastly, the builder adds Axon types for XStream's security
     * settings by including {@code "org.axonframework.**} as a wildcard type.
     * <p>
     * Upon instantiation, several defaults aliases are added to the XStream instance, for example for the {@link
     * GenericDomainEventMessage}, the {@link org.axonframework.commandhandling.GenericCommandMessage} and the {@link
     * org.axonframework.messaging.MetaData} objects among others. Additionally, a MetaData Converter is registered too.
     * Lastly, if the provided Converter instance is of type ChainingConverter, then the {@link
     * XStreamSerializer#registerConverters(ChainingConverter)} function will be called. This will register the {@link
     * Dom4JToByteArrayConverter}, {@link InputStreamToDom4jConverter}, {@link XomToStringConverter} and {@link
     * InputStreamToXomConverter} to the Converter chain.
     *
     * @return a {@link XStreamSerializer}
     * @deprecated in favor of using the {@link #builder()} to construct an instance using a configured {@code XStream}
     * instance. Using this shorthand still works, but will use an {@code XStream} instance that <b>allows
     * everything</b>. Although this works, XStream expects the types or wildcards for the types to be defined to ensure
     * the application stays secure. As such, it is <b>highly recommended</b> to follow their recommended approach.
     */
    @Deprecated
    public static XStreamSerializer defaultSerializer() {
        logger.warn("An unsecured XStream instance allowing all types is used. "
                            + "It is strongly recommended to set the security context yourself instead!",
                    new AxonConfigurationException(
                            "An unsecured XStream instance allowing all types is used. "
                                    + "It is strongly recommended to set the security context yourself instead!"
                    ));
        XStream xStream = new XStream(new CompactDriver());
        xStream.allowTypeHierarchy(Object.class);
        return builder().xStream(xStream)
                        .build();
    }

    /**
     * Instantiate a {@link XStreamSerializer} based on the fields contained in the {@link Builder}. The {@link XStream}
     * instance is configured with several converters for the most common types in Axon.
     *
     * @param builder the {@link Builder} used to instantiate a {@link XStreamSerializer} instance
     */
    protected XStreamSerializer(Builder builder) {
        super(builder);
    }

    @Override
    protected <T> T doSerialize(Object object, Class<T> expectedFormat, XStream xStream) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        xStream.toXML(object, new OutputStreamWriter(baos, getCharset()));
        return convert(baos.toByteArray(), byte[].class, expectedFormat);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public Object doDeserialize(SerializedObject serializedObject, XStream xStream) {
        if ("org.dom4j.Document".equals(serializedObject.getContentType().getName())) {
            return xStream.unmarshal(new Dom4JReader((org.dom4j.Document) serializedObject.getData()));
        }
        if ("nu.xom.Document".equals(serializedObject.getContentType().getName())) {
            return xStream.unmarshal(new XomReader((nu.xom.Document) serializedObject.getData()));
        }
        InputStream serializedData = convert(serializedObject.getData(), serializedObject.getContentType(),
                                             InputStream.class);
        return xStream.fromXML(new InputStreamReader(serializedData, getCharset()));
    }

    @Override
    protected void registerConverters(ChainingConverter converter) {
        converter.registerConverter(Dom4JToByteArrayConverter.class);
        converter.registerConverter(InputStreamToDom4jConverter.class);
        converter.registerConverter(XomToStringConverter.class);
        converter.registerConverter(InputStreamToXomConverter.class);
    }

    /**
     * Builder class to instantiate a {@link XStreamSerializer}.
     * <p>
     * The {@link XStream} is defaulted to a {@link XStream#XStream(HierarchicalStreamDriver)} call, providing a {@link
     * CompactDriver}, the {@link Charset} is defaulted to a {@link Charset#forName(String)} using the {@code UTF-8}
     * character set, the {@link RevisionResolver} defaults to an {@link AnnotationRevisionResolver} and the {@link
     * Converter} defaults to a {@link ChainingConverter}. Lastly, the builder adds Axon types for XStream's security
     * settings by including {@code "org.axonframework.**} as a wildcard type. This can be disabled with the {@link
     * Builder#disableAxonTypeSecurity()} operation when required.
     * <p>
     * Upon instantiation, several defaults aliases are added to the XStream instance, for example for the {@link
     * GenericDomainEventMessage}, the {@link org.axonframework.commandhandling.GenericCommandMessage} and the {@link
     * org.axonframework.messaging.MetaData} objects among others. Additionally, a MetaData Converter is registered too.
     * Lastly, if the provided Converter instance is of type ChainingConverter, then the {@link
     * XStreamSerializer#registerConverters(ChainingConverter)} function will be called. This will register the {@link
     * Dom4JToByteArrayConverter}, {@link InputStreamToDom4jConverter}, {@link XomToStringConverter} and {@link
     * InputStreamToXomConverter} to the Converter chain.
     */
    public static class Builder extends AbstractXStreamSerializer.Builder {

        /**
         * {@inheritDoc} Defaults to a {@link XStream#XStream(HierarchicalStreamDriver)} call, providing the {@link
         * CompactDriver}.
         */
        @Override
        public Builder xStream(@Nonnull XStream xStream) {
            super.xStream(xStream);
            return this;
        }

        @Override
        public Builder charset(@Nonnull Charset charset) {
            super.charset(charset);
            return this;
        }

        @Override
        public Builder revisionResolver(@Nonnull RevisionResolver revisionResolver) {
            super.revisionResolver(revisionResolver);
            return this;
        }

        @Override
        public Builder converter(@Nonnull Converter converter) {
            super.converter(converter);
            return this;
        }

        @Override
        public Builder classLoader(@Nonnull ClassLoader classLoader) {
            super.classLoader(classLoader);
            return this;
        }

        @Override
        public Builder lenientDeserialization() {
            super.lenientDeserialization();
            return this;
        }

        @Override
        public Builder disableAxonTypeSecurity() {
            super.disableAxonTypeSecurity();
            return this;
        }

        /**
         * Initializes a {@link XStreamSerializer} as specified through this Builder.
         *
         * @return a {@link XStreamSerializer} as specified through this Builder
         */
        public XStreamSerializer build() {
            if (xStream == null) {
                logger.warn("An unsecured XStream instance allowing all types is used. "
                                    + "It is strongly recommended to set the security context yourself instead!",
                            new AxonConfigurationException(
                                    "An unsecured XStream instance allowing all types is used. "
                                            + "It is strongly recommended to set the security context yourself instead!"
                            ));
                xStream = new XStream(new CompactDriver());
            }
            return new XStreamSerializer(this);
        }
    }
}
