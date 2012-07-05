package org.axonframework.serializer.bson;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.thoughtworks.xstream.XStream;
import org.axonframework.common.SerializationException;
import org.axonframework.serializer.AbstractXStreamSerializer;
import org.axonframework.serializer.ChainingConverterFactory;
import org.axonframework.serializer.ConverterFactory;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SerializedType;

import java.nio.charset.Charset;

/**
 * XStream based serializer implementation that serializes objects into a Binary JSON structure. This serializer is
 * originally meant for use with a MongoDB based Event Store. It escapes BSON Node names to prevent them containing
 * periods (".").
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DBObjectXStreamSerializer extends AbstractXStreamSerializer {

    /**
     * Initialize the serializer with UTF-8 character set and a default XStream serializer.
     */
    public DBObjectXStreamSerializer() {
        super(new XStream());
    }

    /**
     * Initialize the serializer using the UTF-8 character set. The provided XStream instance  is used to perform
     * the serialization.
     *
     * @param xStream XStream instance to use
     */
    public DBObjectXStreamSerializer(XStream xStream) {
        super(xStream);
    }

    /**
     * Initialize the serializer using the given <code>charset</code>. A default XStream instance (with {@link
     * com.thoughtworks.xstream.io.xml.XppDriver}) is used to perform the serialization.
     *
     * @param charset The character set to use
     */
    public DBObjectXStreamSerializer(Charset charset) {
        super(charset, new XStream());
    }

    /**
     * Initialize the serializer using the given <code>charset</code> and <code>xStream</code> instance. The
     * <code>xStream</code> instance is configured with several converters for the most common types in Axon.
     *
     * @param charset The character set to use
     * @param xStream The XStream instance to use
     */
    public DBObjectXStreamSerializer(Charset charset, XStream xStream) {
        super(charset, xStream);
    }

    /**
     * Initialize the serializer using the given <code>charset</code> and <code>xStream</code> instance. The
     * given <code>converterFactory</code> instance is used to convert between serialized representation types.
     *
     * @param charset          The character set to use
     * @param xStream          The XStream instance to use
     * @param converterFactory The converter factory to provide the converters
     */
    public DBObjectXStreamSerializer(Charset charset, XStream xStream, ConverterFactory converterFactory) {
        super(charset, xStream, converterFactory);
    }

    @Override
    protected void registerConverters(ChainingConverterFactory converterFactory) {
        converterFactory.registerConverter(new DBObjectToStringContentTypeConverter());
        converterFactory.registerConverter(new StringToDBObjectContentTypeConverter());
    }

    @Override
    protected <T> T doSerialize(Object object, Class<T> expectedFormat, XStream xStream) {
        BasicDBObject root = new BasicDBObject();
        getXStream().marshal(object, new DBObjectHierarchicalStreamWriter(root));
        return convert(DBObject.class, expectedFormat, root);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Object doDeserialize(SerializedObject serializedObject, XStream xStream) {
        DBObject serialized = (DBObject) convert(serializedObject.getContentType(),
                                                 DBObject.class, serializedObject.getData());
        return getXStream().unmarshal(new DBObjectHierarchicalStreamReader(serialized));
    }

    @Override
    public Class classForType(SerializedType type) {
        try {
            return getClass().getClassLoader().loadClass(type.getName());
        } catch (ClassNotFoundException e) {
            throw new SerializationException("Cannot load class for expected type: " + type.getName(), e);
        }
    }
}
