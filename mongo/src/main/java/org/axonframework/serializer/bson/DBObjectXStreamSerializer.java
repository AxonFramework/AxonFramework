/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.serializer.bson;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.thoughtworks.xstream.XStream;
import org.axonframework.serializer.AbstractXStreamSerializer;
import org.axonframework.serializer.ChainingConverterFactory;
import org.axonframework.serializer.ConverterFactory;
import org.axonframework.serializer.RevisionResolver;
import org.axonframework.serializer.SerializedObject;

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
     * <p/>
     * An {@link org.axonframework.serializer.AnnotationRevisionResolver} is used to resolve revision for serialized
     * objects.
     */
    public DBObjectXStreamSerializer() {
        super(new XStream());
    }

    /**
     * Initialize the serializer using the UTF-8 character set. The provided XStream instance  is used to perform
     * the serialization.
     * <p/>
     * An {@link org.axonframework.serializer.AnnotationRevisionResolver} is used to resolve the revision for
     * serialized objects.
     *
     * @param xStream XStream instance to use
     */
    public DBObjectXStreamSerializer(XStream xStream) {
        super(xStream);
    }

    /**
     * Initialize the serializer using the UTF-8 character set. The provided XStream instance  is used to perform
     * the serialization, while the given <code>revisionResolver</code> is used to resolve the revision of the
     * serialized object.
     *
     * @param xStream          The XStream instance to serialize objects with
     * @param revisionResolver The instance to resolve revisions with
     */
    public DBObjectXStreamSerializer(XStream xStream, RevisionResolver revisionResolver) {
        super(xStream, revisionResolver);
    }

    /**
     * Initialize the serializer using the given <code>charset</code>. A default XStream instance (with {@link
     * com.thoughtworks.xstream.io.xml.XppDriver}) is used to perform the serialization.
     * <p/>
     * An {@link org.axonframework.serializer.AnnotationRevisionResolver} is used to resolve the revision for
     * serialized objects.
     *
     * @param charset The character set to use
     */
    public DBObjectXStreamSerializer(Charset charset) {
        super(charset, new XStream());
    }

    /**
     * Initialize the serializer using the given <code>charset</code> and <code>xStream</code> instance. The
     * <code>xStream</code> instance is configured with several converters for the most common types in Axon.
     * <p/>
     * An {@link org.axonframework.serializer.AnnotationRevisionResolver} is used to resolve the revision for
     * serialized objects.
     *
     * @param charset The character set to use
     * @param xStream The XStream instance to use
     */
    public DBObjectXStreamSerializer(Charset charset, XStream xStream) {
        super(charset, xStream);
    }

    /**
     * Initialize the serializer using the given <code>charset</code>, <code>xStream</code> and
     * <code>revisionResolver</code> instance. The <code>xStream</code> instance is configured with several converters
     * for the most common types in Axon.
     *
     * @param charset          The character set to use
     * @param xStream          The XStream instance to use
     * @param revisionResolver The instance to resolve revisions with
     */
    public DBObjectXStreamSerializer(Charset charset, XStream xStream, RevisionResolver revisionResolver) {
        super(charset, xStream, revisionResolver);
    }

    /**
     * Initialize the serializer using the given <code>charset</code> and <code>xStream</code> instance. The
     * given <code>converterFactory</code> instance is used to convert between serialized representation types.
     *
     * @param charset          The character set to use
     * @param xStream          The XStream instance to use
     * @param revisionResolver The strategy to use to resolve the revision of an object
     * @param converterFactory The converter factory to provide the converters
     */
    public DBObjectXStreamSerializer(Charset charset, XStream xStream, RevisionResolver revisionResolver,
                                     ConverterFactory converterFactory) {
        super(charset, xStream, revisionResolver, converterFactory);
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
        DBObject serialized = convert(serializedObject.getContentType(), DBObject.class, serializedObject.getData());
        return getXStream().unmarshal(new DBObjectHierarchicalStreamReader(serialized));
    }
}
