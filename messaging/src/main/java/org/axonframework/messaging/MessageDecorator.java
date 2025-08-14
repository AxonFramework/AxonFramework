/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.lang.reflect.Type;

/**
 * Abstract implementation of a {@link Message} that delegates to an existing message.
 * <p>
 * Extend this decorator class to extend the message with additional features.
 *
 * @param <P> The type of {@link #payload() payload} contained in this {@link MessageDecorator}.
 * @author Steven van Beelen
 * @author Rene de Waele
 * @since 3.0.0
 */
public abstract class MessageDecorator<P> implements Message<P> {

    private final Message<P> delegate;

    /**
     * Initializes a new decorator with given {@code delegate} {@link Message}.
     * <p>
     * The decorator delegates to the delegate for the message's {@link #identifier() identifier},
     * {@link Message#type() type}, {@link #payload() payload}, and {@link #metaData() metadata}.
     *
     * @param delegate The {@link Message} delegate.
     */
    protected MessageDecorator(@Nonnull Message<P> delegate) {
        this.delegate = delegate;
    }

    @Override
    @Nonnull
    public String identifier() {
        return delegate.identifier();
    }

    @Override
    @Nonnull
    public MessageType type() {
        return delegate.type();
    }

    @Override
    @Nullable
    public P payload() {
        return delegate.payload();
    }

    @Override
    @Nullable
    public <T> T payloadAs(@Nonnull Type type, @Nullable Converter converter) {
        return delegate.payloadAs(type, converter);
    }

    @Override
    @Nonnull
    public Class<P> payloadType() {
        return delegate.payloadType();
    }

    @Override
    @Nonnull
    public MetaData metaData() {
        return delegate.metaData();
    }

    @Override
    @Nonnull
    public <S> SerializedObject<S> serializePayload(Serializer serializer, Class<S> expectedRepresentation) {
        return delegate.serializePayload(serializer, expectedRepresentation);
    }

    @Override
    public <S> SerializedObject<S> serializeMetaData(Serializer serializer, Class<S> expectedRepresentation) {
        return delegate.serializeMetaData(serializer, expectedRepresentation);
    }

    @Override
    @Nonnull
    public <T> Message<T> withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter) {
        return delegate.withConvertedPayload(type, converter);
    }

    /**
     * Returns the wrapped {@link Message} delegated by this decorator.
     *
     * @return The wrapped {@link Message} delegated by this decorator.
     */
    @Nonnull
    protected Message<P> delegate() {
        return delegate;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder()
                .append(describeType())
                .append("{");
        describeTo(sb);
        return sb.append("}")
                 .toString();
    }

    /**
     * Describe the message specific properties to the given {@code stringBuilder}. Subclasses should override this
     * method, calling the super method and appending their own properties to the end (or beginning).
     * <p>
     * As convention, String values should be enclosed in single quotes, Objects in curly brackets and numeric values
     * may be appended without enclosing. All properties should be preceded by a comma when appending, or finish with a
     * comma when prefixing values.
     *
     * @param stringBuilder The builder to append data to.
     */
    protected void describeTo(StringBuilder stringBuilder) {
        stringBuilder.append("type={")
                     .append(type())
                     .append('}')
                     .append(", payload={")
                     .append(payload())
                     .append('}')
                     .append(", metadata={")
                     .append(metaData())
                     .append('}')
                     .append(", messageIdentifier='")
                     .append(identifier())
                     .append('\'');
    }

    /**
     * Describe the type of message, used in {@link #toString()}.
     * <p>
     * Defaults to the simple class name of the actual instance.
     *
     * @return The type of the message.
     */
    protected String describeType() {
        return getClass().getSimpleName();
    }
}
