/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.serialization;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.MapConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.CannotResolveClassException;
import com.thoughtworks.xstream.mapper.Mapper;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ObjectUtils;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Abstract implementation for XStream based serializers. It provides some helper methods and configuration features
 * independent of the actual format used to marshal to.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractXStreamSerializer implements Serializer {

    private final XStream xStream;
    private final Charset charset;
    private final RevisionResolver revisionResolver;
    private final Converter converter;

    /**
     * Instantiate a {@link AbstractXStreamSerializer} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AbstractXStreamSerializer} instance
     */
    protected AbstractXStreamSerializer(Builder builder) {
        builder.validate();
        this.charset = builder.charset;
        this.xStream = builder.xStream;
        this.converter = builder.converter;
        this.revisionResolver = builder.revisionResolver;

        if (converter instanceof ChainingConverter) {
            registerConverters((ChainingConverter) converter);
        }

        // Message serialization
        xStream.alias("domain-event", GenericDomainEventMessage.class);
        xStream.alias("event", GenericEventMessage.class);
        xStream.alias("command", GenericCommandMessage.class);
        xStream.alias("command-result", GenericCommandResultMessage.class);
        xStream.alias("query", GenericQueryMessage.class);
        xStream.alias("query-response", GenericQueryResponseMessage.class);
        xStream.alias("query-update", GenericSubscriptionQueryUpdateMessage.class);
        xStream.alias("deadline", GenericDeadlineMessage.class);
        xStream.alias("meta-data", MetaData.class);
        xStream.registerConverter(new MetaDataConverter(xStream.getMapper()));
        xStream.registerConverter(new GapAwareTrackingTokenConverter(xStream.getMapper()));

        xStream.addImmutableType(UUID.class, true);
        // For backward compatibility
        xStream.alias("uuid", UUID.class);
    }

    /**
     * Registers any converters that are specific to the type of content written by this serializer.
     *
     * @param converter the Converter to register the converters with
     */
    protected abstract void registerConverters(ChainingConverter converter);

    @Override
    public <T> boolean canSerializeTo(@Nonnull Class<T> expectedRepresentation) {
        return converter.canConvert(byte[].class, expectedRepresentation);
    }

    @Override
    public <T> SerializedObject<T> serialize(Object object, @Nonnull Class<T> expectedType) {
        T result = doSerialize(object, expectedType, xStream);
        return new SimpleSerializedObject<>(result, expectedType, typeForClass(ObjectUtils.nullSafeTypeOf(object)));
    }

    /**
     * Serialize the given {@code object} to the given {@code expectedFormat}. The subclass may use {@link
     * #convert(Object, Class, Class)} to convert the result of the serialization to the expected type.
     *
     * @param object         The object to serialize
     * @param expectedFormat The format in which the serialized object must be returned
     * @param xStream        The XStream instance to serialize with
     * @param <T>            The format in which the serialized object must be returned
     * @return The serialized object
     */
    protected abstract <T> T doSerialize(Object object, Class<T> expectedFormat, XStream xStream);

    /**
     * Deserialize the given {@code serializedObject}.
     *
     * @param serializedObject The instance containing the serialized format of the object
     * @param xStream          The XStream instance to deserialize with
     * @return the deserialized object
     */
    protected abstract Object doDeserialize(SerializedObject serializedObject, XStream xStream);

    /**
     * Convert the given {@code source}, of type {@code sourceType} to the given {@code targetType}.
     *
     * @param <S>        The type of data that needs to be converted
     * @param <T>        The target type of the conversion
     * @param source     The object to convert
     * @param sourceType The source type of the conversion
     * @param targetType The target type of the conversion
     * @return The converted object
     */
    protected <S, T> T convert(S source, Class<S> sourceType, Class<T> targetType) {
        return getConverter().convert(source, sourceType, targetType);
    }

    private String revisionOf(Class<?> type) {
        return revisionResolver.revisionOf(type);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S, T> T deserialize(@Nonnull SerializedObject<S> serializedObject) {
        if (SerializedType.emptyType().equals(serializedObject.getType())) {
            return null;
        }
        if (UnknownSerializedType.class.isAssignableFrom(classForType(serializedObject.getType()))) {
            return (T) new UnknownSerializedType(this, serializedObject);
        }
        return (T) doDeserialize(serializedObject, xStream);
    }

    @Override
    public Class classForType(@Nonnull SerializedType type) {
        if (SerializedType.emptyType().equals(type)) {
            return Void.class;
        }
        try {
            return xStream.getMapper().realClass(type.getName());
        } catch (CannotResolveClassException e) {
            return UnknownSerializedType.class;
        }
    }

    @Override
    public SerializedType typeForClass(Class type) {
        if (type == null || Void.TYPE.equals(type) || Void.class.equals(type)) {
            return SimpleSerializedType.emptyType();
        }
        return new SimpleSerializedType(typeIdentifierOf(type), revisionOf(type));
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
     * for sub-packages of the provided package.
     * <p/>
     * E.g. an alias of "axon-modelling" for the package "org.axonframework.modelling" will use "axon-modelling.command"
     * for the package "org.axonframework.modelling.command".
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
     * @see com.thoughtworks.xstream.XStream
     */
    public XStream getXStream() {
        return xStream;
    }

    /**
     * Returns the character set used to convert character to bytes and vice versa.
     *
     * @return the character set used to convert character to bytes and vice versa
     */
    public Charset getCharset() {
        return charset;
    }

    /**
     * Returns the Converter used by this serialized. The converter factory allows registration of ContentTypeConverters
     * needed by the upcasters.
     *
     * @return the Converter used by this serializer
     */
    @Override
    public Converter getConverter() {
        return converter;
    }

    /**
     * Returns the type identifier for the given {@code type}. It uses the aliasing rules configured in XStream.
     *
     * @param type The type to get the type identifier of
     * @return A String containing the type identifier of the given class
     */
    private String typeIdentifierOf(Class<?> type) {
        return xStream.getMapper().serializedClass(type);
    }

    /**
     * Abstract Builder class to instantiate {@link AbstractXStreamSerializer}.
     * <p>
     * The {@link Charset} is defaulted to a {@link Charset#forName(String)} using the {@code UTF-8} character set, the
     * {@link RevisionResolver} defaults to an {@link AnnotationRevisionResolver} and the {@link Converter} defaults to
     * a {@link ChainingConverter}. The {@link XStream} is a <b>hard requirement</b> and as such should be provided.
     * Lastly, the builder adds Axon types for XStream's security settings by including {@code "org.axonframework.**} as
     * a wildcard type. This can be disabled with the {@link Builder#disableAxonTypeSecurity()} operation when
     * required.
     * <p>
     * Upon instantiation, several defaults aliases are added to the XStream instance, for example for the {@link
     * GenericDomainEventMessage}, the {@link GenericCommandMessage} and the {@link MetaData} objects among others.
     * Additionally, a MetaData Converter is registered too. Lastly, if the provided Converter instance is of type
     * ChainingConverter, then the {@link AbstractXStreamSerializer#registerConverters(ChainingConverter)} function will
     * be called. Depending on the AbstractXStreamSerializer, this will add a number of Converter instances to the
     * chain.
     */
    public abstract static class Builder {

        protected XStream xStream;
        private Charset charset = StandardCharsets.UTF_8;
        private RevisionResolver revisionResolver = new AnnotationRevisionResolver();
        private Converter converter = new ChainingConverter();
        private boolean lenientDeserialization = false;
        private boolean axonTypeSecurity = true;
        private ClassLoader classLoader;

        /**
         * Sets the {@link XStream} used to perform the serialization of objects to XML, and vice versa.
         *
         * @param xStream the {@link XStream} used to perform the serialization of objects to XML, and vice versa
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder xStream(@Nonnull XStream xStream) {
            assertNonNull(xStream, "XStream may not be null");
            this.xStream = xStream;
            return this;
        }

        /**
         * Sets the {@link Charset} used for the in- and output streams required by {@link XStream} for the to and from
         * xml function calls. Defaults to a {@link Charset#forName(String)} using the {@code UTF-8} character set.
         *
         * @param charset the {@link Charset} used for the in- and output streams required by {@link XStream}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder charset(@Nonnull Charset charset) {
            assertNonNull(charset, "Charset may not be null");
            this.charset = charset;
            return this;
        }

        /**
         * Sets the {@link RevisionResolver} used to resolve the revision from an object to be serialized. Defaults to
         * an {@link AnnotationRevisionResolver} which resolves the revision based on the contents of the {@link
         * org.axonframework.serialization.Revision} annotation on the serialized classes.
         *
         * @param revisionResolver a {@link RevisionResolver} used to resolve the revision from an object to be
         *                         serialized
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder revisionResolver(@Nonnull RevisionResolver revisionResolver) {
            assertNonNull(revisionResolver, "RevisionResolver may not be null");
            this.revisionResolver = revisionResolver;
            return this;
        }


        /**
         * Sets the {@link Converter} used as a converter factory providing converter instances utilized by upcasters to
         * convert between different content types. Defaults to a {@link ChainingConverter}.
         *
         * @param converter a {@link Converter} used as a converter factory providing converter instances utilized by
         *                  upcasters to convert between different content types
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder converter(@Nonnull Converter converter) {
            assertNonNull(converter, "Converter may not be null");
            this.converter = converter;
            return this;
        }

        /**
         * Sets the {@link ClassLoader} used as an override for default {@code ClassLoader} used in the {@link XStream}.
         * The same solution could thus be achieved by configuring the `XStream` instance directly.
         *
         * @param classLoader a {@link ClassLoader} used as a class loader in {@link XStream}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder classLoader(@Nonnull ClassLoader classLoader) {
            assertNonNull(classLoader, "ClassLoader may not be null");
            this.classLoader = classLoader;
            return this;
        }

        /**
         * Configures the underlying XStream instance to be lenient when deserializing data into Java objects.
         * Specifically sets the {@link XStream#ignoreUnknownElements()}.
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder lenientDeserialization() {
            this.lenientDeserialization = true;
            return this;
        }

        /**
         * Configures the underlying {@link XStream} instance to <b>not</b> include Axon's classes by default.
         * Concretely, the {@link XStream#allowTypesByWildcard(String[])} method will not be invoked with {@code
         * "org.axonframework.**"}.
         * <p>
         * It is recommended to disable this setting when complete control is required over the allowed types in this
         * serializer's {@code XStream} instance.
         *
         * @return the current Builder instance for fluent interfacing
         */
        public Builder disableAxonTypeSecurity() {
            this.axonTypeSecurity = false;
            return this;
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(xStream, "The XStream instance is a hard requirement and should be provided");
            if (lenientDeserialization) {
                xStream.ignoreUnknownElements();
            }
            if (classLoader != null) {
                xStream.setClassLoader(classLoader);
            }
            if (axonTypeSecurity) {
                xStream.allowTypesByWildcard(new String[]{"org.axonframework.**"});
            }
        }
    }

    /**
     * Class that marshals MetaData in the least verbose way.
     */
    private static final class MetaDataConverter extends MapConverter {

        public MetaDataConverter(Mapper mapper) {
            super(mapper);
        }

        @Override
        public boolean canConvert(Class type) {
            return MetaData.class.equals(type);
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            MetaData metaData = (MetaData) source;
            if (!metaData.isEmpty()) {
                super.marshal(new HashMap<>(metaData), writer, context);
            }
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            if (!reader.hasMoreChildren()) {
                return MetaData.emptyInstance();
            }
            Map<String, Object> contents = new HashMap<>();
            populateMap(reader, context, contents);
            if (contents.isEmpty()) {
                return MetaData.emptyInstance();
            } else {
                return MetaData.from(contents);
            }
        }
    }
}
