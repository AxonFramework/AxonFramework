/*
 * Copyright (c) 2010-2016. Axon Framework
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
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.saga.AnnotatedSaga;
import org.axonframework.eventhandling.saga.AssociationValue;
import org.axonframework.eventhandling.saga.AssociationValues;
import org.axonframework.eventhandling.saga.AssociationValuesImpl;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.messaging.MetaData;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Abstract implementation for XStream based serializers. It provides some helper methods and configuration features
 * independent of the actual format used to marshal to.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractXStreamSerializer implements Serializer {

    private static final Charset DEFAULT_CHARSET_NAME = Charset.forName("UTF-8");

    private final XStream xStream;
    private final Charset charset;
    private final RevisionResolver revisionResolver;
    private final ConverterFactory converterFactory;

    /**
     * Initialize a generic serializer using the UTF-8 character set. The provided XStream instance  is used to perform
     * the serialization.
     * <p/>
     * An {@link AnnotationRevisionResolver} is used to resolve the revision for serialized objects.
     *
     * @param xStream XStream instance to use
     */
    protected AbstractXStreamSerializer(XStream xStream) {
        this(xStream, new AnnotationRevisionResolver());
    }

    /**
     * Initialize a generic serializer using the UTF-8 character set. The provided XStream instance  is used to perform
     * the serialization.
     *
     * @param xStream          XStream instance to use
     * @param revisionResolver The strategy to use to resolve the revision of an object
     */
    protected AbstractXStreamSerializer(XStream xStream, RevisionResolver revisionResolver) {
        this(DEFAULT_CHARSET_NAME, xStream, revisionResolver);
    }

    /**
     * Initialize the serializer using the given {@code charset} and {@code xStream} instance. The
     * {@code xStream} instance is configured with several converters for the most common types in Axon.
     * <p/>
     * An {@link AnnotationRevisionResolver} is used to resolve revision for serialized objects.
     *
     * @param charset The character set to use
     * @param xStream The XStream instance to use
     */
    protected AbstractXStreamSerializer(Charset charset, XStream xStream) {
        this(charset, xStream, new AnnotationRevisionResolver(), new ChainingConverterFactory());
    }

    /**
     * Initialize the serializer using the given {@code charset} and {@code xStream} instance. The
     * {@code xStream} instance is configured with several converters for the most common types in Axon.
     *
     * @param charset          The character set to use
     * @param xStream          The XStream instance to use
     * @param revisionResolver The strategy to use to resolve the revision of an object
     */
    protected AbstractXStreamSerializer(Charset charset, XStream xStream, RevisionResolver revisionResolver) {
        this(charset, xStream, revisionResolver, new ChainingConverterFactory());
    }

    /**
     * Initialize the serializer using the given {@code charset}, {@code xStream} instance,
     * {@code revisionResolver} and {@code converterFactory}. The {@code xStream} instance is configured
     * with several converters for the most common types in Axon.
     *
     * @param charset          The character set to use
     * @param xStream          The XStream instance to use
     * @param revisionResolver The strategy to use to resolve the revision of an object
     * @param converterFactory The ConverterFactory providing the necessary content converters
     */
    protected AbstractXStreamSerializer(Charset charset, XStream xStream, RevisionResolver revisionResolver,
                                        ConverterFactory converterFactory) {
        Assert.notNull(charset, "charset may not be null");
        Assert.notNull(xStream, "xStream may not be null");
        Assert.notNull(converterFactory, "converterFactory may not be null");
        Assert.notNull(revisionResolver, "revisionResolver may not be null");
        this.charset = charset;
        this.xStream = xStream;
        this.converterFactory = converterFactory;
        this.revisionResolver = revisionResolver;
        if (converterFactory instanceof ChainingConverterFactory) {
            registerConverters((ChainingConverterFactory) converterFactory);
        }
        xStream.addImmutableType(UUID.class, true);

        // Message serialization
        xStream.alias("domain-event", GenericDomainEventMessage.class);
        xStream.alias("event", GenericEventMessage.class);
        xStream.alias("command", GenericCommandMessage.class);

        // Configuration to enhance Saga serialization
        xStream.addDefaultImplementation(AssociationValuesImpl.class, AssociationValues.class);
        xStream.aliasField("associations", AnnotatedSaga.class, "associationValues");
        xStream.alias("association", AssociationValue.class);
        xStream.aliasField("key", AssociationValue.class, "propertyKey");
        xStream.aliasField("value", AssociationValue.class, "propertyValue");

        // for backward compatibility
        xStream.alias("uuid", UUID.class);

        xStream.alias("meta-data", MetaData.class);
        xStream.registerConverter(new MetaDataConverter(xStream.getMapper()));
    }

    /**
     * Registers any converters that are specific to the type of content written by this serializer.
     *
     * @param converterFactory the ConverterFactory to register the converters with
     */
    protected abstract void registerConverters(ChainingConverterFactory converterFactory);

    @Override
    public <T> boolean canSerializeTo(Class<T> expectedRepresentation) {
        return converterFactory.hasConverter(byte[].class, expectedRepresentation);
    }

    @Override
    public <T> SerializedObject<T> serialize(Object object, Class<T> expectedType) {
        T result = doSerialize(object, expectedType, xStream);
        return new SimpleSerializedObject<>(result, expectedType, typeForClass(object.getClass()));
    }

    /**
     * Serialize the given {@code object} to the given {@code expectedFormat}. The subclass may use {@link
     * #convert(Class, Class, Object)} to convert the result of the serialization to the expected type.
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
     * @param sourceType The type of data that needs to be converted. Should be a content type identifier, not
     *                   necessarily the result of {@code source.getClass()}.
     * @param targetType The target type of the conversion
     * @param source     The object to convert
     * @param <S>        The type of data that needs to be converted
     * @param <T>        The target type of the conversion
     * @return The converted object
     */
    protected <S, T> T convert(Class<S> sourceType, Class<T> targetType, S source) {
        return getConverterFactory().getConverter(sourceType, targetType).convert(source);
    }

    private String revisionOf(Class<?> type) {
        return revisionResolver.revisionOf(type);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S, T> T deserialize(SerializedObject<S> serializedObject) {
        return (T) doDeserialize(serializedObject, xStream);
    }

    @Override
    public Class classForType(SerializedType type) {
        try {
            return xStream.getMapper().realClass(type.getName());
        } catch (CannotResolveClassException e) {
            throw new UnknownSerializedTypeException(type, e);
        }
    }

    @Override
    public SerializedType typeForClass(Class type) {
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
     * Returns the character set used to convert character to bytes and vice versa.
     *
     * @return the character set used to convert character to bytes and vice versa
     */
    public Charset getCharset() {
        return charset;
    }

    /**
     * Returns the ConverterFactory used by this serialized. The converter factory allows registration of
     * ContentTypeConverters needed by the upcasters.
     *
     * @return the ConverterFactory used by this serialized
     */
    @Override
    public ConverterFactory getConverterFactory() {
        return converterFactory;
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
