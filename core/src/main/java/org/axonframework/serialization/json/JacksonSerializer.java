/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.serialization.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import org.axonframework.messaging.metadata.MetaData;
import org.axonframework.serialization.*;

import java.io.IOException;

/**
 * Serializer implementation that uses Jackson to serialize objects into a JSON format. Although the Jackson serializer
 * requires classes to be compatible with this specific serializer, it providers much more compact serialization, while
 * still being human readable.
 *
 * @author Allard Buijze
 * @since 2.2
 */
public class JacksonSerializer implements Serializer {

    private final RevisionResolver revisionResolver;
    private final ConverterFactory converterFactory;
    private final ObjectMapper objectMapper;
    private final ClassLoader classLoader;

    /**
     * Initialize the serializer with a default ObjectMapper instance. Revisions are resolved using {@link
     * org.axonframework.serialization.Revision @Revision} annotations on the serialized classes.
     */
    public JacksonSerializer() {
        this(new AnnotationRevisionResolver(), new ChainingConverterFactory());
    }

    /**
     * Initialize the serializer with the given <code>objectMapper</code> to serialize and parse the objects to JSON.
     * This objectMapper allows for customization of the serialized form.
     *
     * @param objectMapper The objectMapper to serialize objects and parse JSON with
     */
    public JacksonSerializer(ObjectMapper objectMapper) {
        this(objectMapper, new AnnotationRevisionResolver(), new ChainingConverterFactory());
    }

    /**
     * Initialize the serializer using a default ObjectMapper instance, using the given <code>revisionResolver</code>
     * to define revision for each object to serialize, and given <code>converterFactory</code> to be used by
     * upcasters.
     *
     * @param revisionResolver The strategy to use to resolve the revision of an object
     * @param converterFactory The factory providing the converter instances for upcasters
     */
    public JacksonSerializer(RevisionResolver revisionResolver, ConverterFactory converterFactory) {
        this(new ObjectMapper(), revisionResolver, converterFactory);
    }

    /**
     * Initialize the serializer with the given <code>objectMapper</code> to serialize and parse the objects to JSON.
     * This objectMapper allows for customization of the serialized form. The given <code>revisionResolver</code> is
     * used to resolve the revision from an object to be serialized.
     *
     * @param objectMapper     The objectMapper to serialize objects and parse JSON with
     * @param revisionResolver The strategy to use to resolve the revision of an object
     */
    public JacksonSerializer(ObjectMapper objectMapper, RevisionResolver revisionResolver) {
        this(objectMapper, revisionResolver, new ChainingConverterFactory());
    }

    /**
     * Initialize the serializer with the given <code>objectMapper</code> to serialize and parse the objects to JSON.
     * This objectMapper allows for customization of the serialized form. The given <code>revisionResolver</code> is
     * used to resolve the revision from an object to be serialized. The given <code>converterFactory</code> is the
     * converter factory used by upcasters to convert between content types.
     *
     * @param objectMapper     The objectMapper to serialize objects and parse JSON with
     * @param revisionResolver The strategy to use to resolve the revision of an object
     * @param converterFactory The factory providing the converter instances for upcasters
     */
    public JacksonSerializer(ObjectMapper objectMapper, RevisionResolver revisionResolver,
                             ConverterFactory converterFactory) {
        this(objectMapper, revisionResolver, converterFactory, null);
    }

    /**
     * Initialize the serializer with the given <code>objectMapper</code> to serialize and parse the objects to JSON.
     * This objectMapper allows for customization of the serialized form. The given <code>revisionResolver</code> is
     * used to resolve the revision from an object to be serialized. The given <code>converterFactory</code> is the
     * converter factory used by upcasters to convert between content types.
     *
     * @param objectMapper     The objectMapper to serialize objects and parse JSON with
     * @param revisionResolver The strategy to use to resolve the revision of an object
     * @param converterFactory The factory providing the converter instances for upcasters
     * @param classLoader      The class loader to load classes with when deserializing
     */
    public JacksonSerializer(ObjectMapper objectMapper, RevisionResolver revisionResolver,
                             ConverterFactory converterFactory, ClassLoader classLoader) {
        this.revisionResolver = revisionResolver;
        this.converterFactory = converterFactory;
        this.objectMapper = objectMapper;
        this.classLoader = classLoader == null ? getClass().getClassLoader() : classLoader;
        this.objectMapper.registerModule(
                new SimpleModule("Axon-Jackson Module")
                        .addDeserializer(MetaData.class, new MetaDataDeserializer()));
        this.objectMapper.registerModule(new JSR310Module());
        if (converterFactory instanceof ChainingConverterFactory) {
            registerConverters((ChainingConverterFactory) converterFactory);
        }
    }

    /**
     * Registers converters with the given <code>converterFactory</code> which depend on the actual contents of the
     * serialized for to represent a JSON format.
     *
     * @param converterFactory The ChainingConverterFactory instance to register the converters with.
     */
    protected void registerConverters(ChainingConverterFactory converterFactory) {
        converterFactory.registerConverter(new JsonNodeToByteArrayConverter(objectMapper));
        converterFactory.registerConverter(new ByteArrayToJsonNodeConverter(objectMapper));
    }

    @Override
    public <T> SerializedObject<T> serialize(Object object, Class<T> expectedRepresentation) {

        try {
            if (String.class.equals(expectedRepresentation)) {
                //noinspection unchecked
                return new SimpleSerializedObject<>((T) getWriter().writeValueAsString(object),
                                                     expectedRepresentation, typeForClass(object.getClass()));
            }

            byte[] serializedBytes = getWriter().writeValueAsBytes(object);
            T serializedContent = converterFactory.getConverter(byte[].class, expectedRepresentation)
                                                  .convert(serializedBytes);
            return new SimpleSerializedObject<>(serializedContent, expectedRepresentation,
                                                 typeForClass(object.getClass()));
        } catch (JsonProcessingException e) {
            throw new SerializationException("Unable to serialize object", e);
        }
    }

    /**
     * Returns the ObjectMapper used by this serializer, allowing for configuration of the serialization settings.
     *
     * @return the ObjectMapper instance used by his serializer
     */
    public final ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Provides the ObjectWriter, with which objects are serialized to JSON form. This method may be overridden to
     * change the configuration of the writer to use.
     *
     * @return The writer to serialize objects with
     */
    protected ObjectWriter getWriter() {
        return objectMapper.writer();
    }

    /**
     * Provides the ObjectReader, with which objects are read from the JSON form. This method may be overridden to
     * change the configuration of the reader to use.
     *
     * @param type The type of object to create a reader for
     * @return The writer to serialize objects with
     */
    protected ObjectReader getReader(Class<?> type) {
        return objectMapper.reader(type);
    }

    @Override
    public <T> boolean canSerializeTo(Class<T> expectedRepresentation) {
        return JsonNode.class.equals(expectedRepresentation)
                || String.class.equals(expectedRepresentation)
                || converterFactory.hasConverter(byte[].class, expectedRepresentation);
    }

    @Override
    public <S, T> T deserialize(SerializedObject<S> serializedObject) {
        try {
            if (JsonNode.class.equals(serializedObject.getContentType())) {
                return getReader(classForType(serializedObject.getType()))
                        .readValue((JsonNode) serializedObject.getData());
            }
            SerializedObject<byte[]> byteSerialized = converterFactory.getConverter(serializedObject.getContentType(),
                                                                                    byte[].class)
                                                                      .convert(serializedObject);
            return getReader(classForType(serializedObject.getType())).readValue(byteSerialized.getData());
        } catch (IOException e) {
            throw new SerializationException("Error while deserializing object", e);
        }
    }

    @Override
    public Class classForType(SerializedType type) throws UnknownSerializedTypeException {
        try {
            return classLoader.loadClass(resolveClassName(type));
        } catch (ClassNotFoundException e) {
            throw new UnknownSerializedTypeException(type, e);
        }
    }

    /**
     * Resolve the class name from the given <code>serializedType</code>. This method may be overridden to customize
     * the names used to denote certain classes, for example, by leaving out a certain base package for brevity.
     *
     * @param serializedType The serialized type to resolve the class name for
     * @return The fully qualified name of the class to load
     */
    protected String resolveClassName(SerializedType serializedType) {
        return serializedType.getName();
    }

    @Override
    public SerializedType typeForClass(Class type) {
        return new SimpleSerializedType(type.getName(), revisionResolver.revisionOf(type));
    }

    @Override
    public ConverterFactory getConverterFactory() {
        return converterFactory;
    }
}
