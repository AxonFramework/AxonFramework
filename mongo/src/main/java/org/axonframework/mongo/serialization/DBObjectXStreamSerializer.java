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

package org.axonframework.mongo.serialization;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.thoughtworks.xstream.XStream;
import org.axonframework.serialization.AbstractXStreamSerializer;
import org.axonframework.serialization.AnnotationRevisionResolver;
import org.axonframework.serialization.ChainingConverter;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.RevisionResolver;
import org.axonframework.serialization.SerializedObject;

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
     * Instantiate a {@link DBObjectXStreamSerializer} based on the fields contained in the {@link Builder}.
     * The {@link XStream} instance is configured with several converters for the most common types in Axon.
     *
     * @param builder the {@link Builder} used to instantiate a {@link DBObjectXStreamSerializer} instance
     */
    protected DBObjectXStreamSerializer(Builder builder) {
        super(builder);
    }

    /**
     * Instantiate a Builder to be able to create a {@link DBObjectXStreamSerializer}.
     * <p>
     * The {@link XStream} is defaulted to a {@link XStream#XStream()} call, the {@link Charset} is defaulted to a
     * {@link Charset#forName(String)} using the {@code UTF-8} character set, the {@link RevisionResolver} defaults to
     * an {@link AnnotationRevisionResolver} and the {@link Converter} defaults to a {@link ChainingConverter}.
     * <p>
     * Upon instantiation, several defaults aliases are added to the XStream instance, for example for the
     * {@link org.axonframework.eventsourcing.GenericDomainEventMessage}, the
     * {@link org.axonframework.commandhandling.GenericCommandMessage}, the
     * {@link org.axonframework.eventhandling.saga.AnnotatedSaga} and the {@link org.axonframework.messaging.MetaData}
     * objects among others. Additionally, a {@link MetaDataConverter} is registered too. Lastly, if the
     * provided Converter instance is of type ChainingConverter, then the
     * {@link DBObjectXStreamSerializer#registerConverters(ChainingConverter)} function will be called. This will
     * register the
     * {@link DBObjectToStringContentTypeConverter}, {@link DocumentToStringContentTypeConverter} and
     * {@link StringToDBObjectContentTypeConverter} to the Converter chain.
     *
     * @return a Builder to be able to create a {@link DBObjectXStreamSerializer}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected void registerConverters(ChainingConverter converter) {
        converter.registerConverter(new DBObjectToStringContentTypeConverter());
        converter.registerConverter(new DocumentToStringContentTypeConverter());
        converter.registerConverter(new StringToDBObjectContentTypeConverter());
    }

    @Override
    protected <T> T doSerialize(Object object, Class<T> expectedFormat, XStream xStream) {
        BasicDBObject root = new BasicDBObject();
        getXStream().marshal(object, new DBObjectHierarchicalStreamWriter(root));
        return convert(root, DBObject.class, expectedFormat);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Object doDeserialize(SerializedObject serializedObject, XStream xStream) {
        DBObject serialized = convert(serializedObject.getData(), serializedObject.getContentType(), DBObject.class);
        return getXStream().unmarshal(new DBObjectHierarchicalStreamReader(serialized));
    }

    /**
     * Builder class to instantiate a {@link DBObjectXStreamSerializer}.
     * <p>
     * The {@link XStream} is defaulted to a {@link XStream#XStream()} call, the {@link Charset} is defaulted to a
     * {@link Charset#forName(String)} using the {@code UTF-8} character set, the {@link RevisionResolver} defaults to
     * an {@link AnnotationRevisionResolver} and the {@link Converter} defaults to a {@link ChainingConverter}.
     * <p>
     * Upon instantiation, several defaults aliases are added to the XStream instance, for example for the
     * {@link org.axonframework.eventsourcing.GenericDomainEventMessage}, the
     * {@link org.axonframework.commandhandling.GenericCommandMessage}, the
     * {@link org.axonframework.eventhandling.saga.AnnotatedSaga} and the {@link org.axonframework.messaging.MetaData}
     * objects among others. Additionally, a {@link MetaDataConverter} is registered too. Lastly, if the
     * provided Converter instance is of type ChainingConverter, then the
     * {@link DBObjectXStreamSerializer#registerConverters(ChainingConverter)} function will be called. This will
     * register the
     * {@link DBObjectToStringContentTypeConverter}, {@link DocumentToStringContentTypeConverter} and
     * {@link StringToDBObjectContentTypeConverter} to the Converter chain.
     */
    public static class Builder extends AbstractXStreamSerializer.Builder {

        public Builder() {
            xStream(new XStream());
        }

        @Override
        public Builder xStream(XStream xStream) {
            super.xStream(xStream);
            return this;
        }

        @Override
        public Builder charset(Charset charset) {
            super.charset(charset);
            return this;
        }

        @Override
        public Builder revisionResolver(RevisionResolver revisionResolver) {
            super.revisionResolver(revisionResolver);
            return this;
        }

        @Override
        public Builder converter(Converter converter) {
            super.converter(converter);
            return this;
        }

        /**
         * Initializes a {@link DBObjectXStreamSerializer} as specified through this Builder.
         *
         * @return a {@link DBObjectXStreamSerializer} as specified through this Builder
         */
        public DBObjectXStreamSerializer build() {
            return new DBObjectXStreamSerializer(this);
        }
    }
}
