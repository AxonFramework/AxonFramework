package org.axonframework.serializer;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.MapConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import org.axonframework.common.SerializationException;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.domain.MetaData;
import org.joda.time.DateTime;

import java.lang.reflect.Constructor;
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
    private ConverterFactory converterFactory;

    /**
     * Initialize a generic serializer using the UTF-8 character set and a default XStream instance.
     */
    protected AbstractXStreamSerializer() {
        this(DEFAULT_CHARSET_NAME);
    }

    /**
     * Initialize a generic serializer using the UTF-8 character set. The provided XStream instance  is used to perform
     * the serialization.
     *
     * @param xStream XStream instance to use
     */
    protected AbstractXStreamSerializer(XStream xStream) {
        this(DEFAULT_CHARSET_NAME, xStream);
    }

    /**
     * Initialize the serializer using the given <code>charset</code>. A default XStream instance (with {@link
     * com.thoughtworks.xstream.io.xml.XppDriver}) is used to perform the serialization.
     *
     * @param charset The character set to use
     */
    public AbstractXStreamSerializer(Charset charset) {
        this(charset, new XStream());
    }

    /**
     * Initialize the serializer using the given <code>charset</code> and <code>xStream</code> instance. The
     * <code>xStream</code> instance is configured with several converters for the most common types in Axon.
     *
     * @param charset The character set to use
     * @param xStream The XStream instance to use
     */
    public AbstractXStreamSerializer(Charset charset, XStream xStream) {
        this(charset, xStream, new ChainingConverterFactory());
    }

    /**
     * Registers any converters that are specific to the type of content written by this serializer.
     *
     * @param converterFactory the ConverterFactory to register the converters with
     */
    protected abstract void registerConverters(ChainingConverterFactory converterFactory);

    /**
     * Initialize the serializer using the given <code>charset</code>, <code>xStream</code> instance and
     * <code>converterFactory</code>. The <code>xStream</code> instance is configured with several converters for the
     * most common types in Axon.
     *
     * @param charset          The character set to use
     * @param xStream          The XStream instance to use
     * @param converterFactory The ConverterFactory providing the necessary content converters
     */
    public AbstractXStreamSerializer(Charset charset, XStream xStream, ConverterFactory converterFactory) {
        this.charset = charset;
        this.xStream = xStream;
        this.converterFactory = converterFactory;
        if (converterFactory instanceof ChainingConverterFactory) {
            registerConverters((ChainingConverterFactory) converterFactory);
        }
        xStream.registerConverter(new JodaTimeConverter());
        xStream.addImmutableType(UUID.class);
        xStream.aliasPackage("axon.domain", "org.axonframework.domain");
        xStream.aliasPackage("axon.es", "org.axonframework.eventsourcing");

        xStream.alias("domain-event", GenericDomainEventMessage.class);
        xStream.alias("event", GenericEventMessage.class);

        // for backward compatibility
        xStream.alias("localDateTime", DateTime.class);
        xStream.alias("dateTime", DateTime.class);
        xStream.alias("uuid", UUID.class);
        xStream.useAttributeFor("eventRevision", EventMessage.class);

        xStream.alias("meta-data", MetaData.class);
        xStream.registerConverter(new MetaDataConverter(xStream.getMapper()));
    }

    @Override
    public <T> boolean canSerializeTo(Class<T> expectedRepresentation) {
        return converterFactory.hasConverter(byte[].class, expectedRepresentation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> SerializedObject<T> serialize(Object object, Class<T> expectedType) {
        T result = doSerialize(object, expectedType, xStream);
        return new SimpleSerializedObject<T>(result, expectedType, typeIdentifierOf(object.getClass()),
                                             revisionOf(object.getClass()));
    }

    /**
     * Serialize the given <code>object</code> to the given <code>expectedFormat</code>. The subclass may use {@link
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
     * Deserialize the given <code>serializedObject</code>.
     *
     * @param serializedObject The instance containing the serialized format of the object
     * @param xStream          The XStream instance to deserialize with
     * @return the deserialized object
     */
    protected abstract Object doDeserialize(SerializedObject serializedObject, XStream xStream);

    /**
     * Convert the given <code>source</code>, of type <code>sourceType</code> to the given <code>targetType</code>.
     *
     * @param sourceType The type of data that needs to be converted. Should be a content type identifier, not
     *                   necessarily the result of <code>source.getClass()</code>.
     * @param targetType The target type of the conversion
     * @param source     The object to convert
     * @param <S>        The type of data that needs to be converted
     * @param <T>        The target type of the conversion
     * @return The converted object
     */
    protected <S, T> T convert(Class<S> sourceType, Class<T> targetType, S source) {
        return getConverterFactory().getConverter(sourceType, targetType).convert(source);
    }

    /**
     * Returns the revision number for the given <code>type</code>. The default implementation checks for an {@link
     * Revision @Revision} annotation, and returns <code>0</code> if none was found. This method can be safely
     * overridden by subclasses.
     * <p/>
     * The revision number is used by upcasters to decide whether they need to process a certain serialized event.
     * Generally, the revision number needs to be increased each time the structure of an event has been changed in an
     * incompatible manner.
     *
     * @param type The type for which to return the revision number
     * @return the revision number for the given <code>type</code>
     */
    protected String revisionOf(Class<?> type) {
        Revision revision = type.getAnnotation(Revision.class);
        return revision == null ? null : revision.value();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> Object deserialize(SerializedObject<T> serializedObject) {
        return doDeserialize(serializedObject, xStream);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class classForType(SerializedType type) {
        return xStream.getMapper().realClass(type.getName());
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
    public ConverterFactory getConverterFactory() {
        return converterFactory;
    }

    /**
     * Returns the type identifier for the given <code>type</code>. It uses the aliasing rules configured in XStream.
     *
     * @param type The type to get the type identifier of
     * @return A String containing the type identifier of the given class
     */
    protected String typeIdentifierOf(Class<?> type) {
        return xStream.getMapper().serializedClass(type);
    }


    /**
     * XStream Converter to serialize DateTime classes as a String.
     */
    private static final class JodaTimeConverter implements Converter {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean canConvert(Class type) {
            return type != null && DateTime.class.getPackage().equals(type.getPackage());
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
            } catch (Exception e) { // NOSONAR
                throw new SerializationException(String.format(
                        "An exception occurred while deserializing a Joda Time object: %s",
                        context.getRequiredType().getSimpleName()), e);
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
                super.marshal(new HashMap<String, Object>(metaData), writer, context);
            }
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            if (!reader.hasMoreChildren()) {
                return MetaData.emptyInstance();
            }
            Map<String, Object> contents = new HashMap<String, Object>();
            populateMap(reader, context, contents);
            if (contents.isEmpty()) {
                return MetaData.emptyInstance();
            } else {
                return MetaData.from(contents);
            }
        }
    }
}
