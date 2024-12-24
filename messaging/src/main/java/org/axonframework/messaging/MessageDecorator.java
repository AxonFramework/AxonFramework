/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.io.Serial;

/**
 * Abstract implementation of a {@link Message} that delegates to an existing message.
 * <p>
 * Extend this decorator class to extend the message with additional features.
 *
 * @param <P> The type of {@link #getPayload() payload} contained in this {@link MessageDecorator}.
 * @author Steven van Beelen
 * @author Rene de Waele
 * @since 3.0.0
 */
public abstract class MessageDecorator<P> implements Message<P> {

    @Serial
    private static final long serialVersionUID = 3969631713723578521L;

    private final Message<P> delegate;

    /**
     * Initializes a new decorator with given {@code delegate} {@link Message}.
     * <p>
     * The decorator delegates to the delegate for the message's {@link #getIdentifier() identifier},
     * {@link #name() type}, {@link #getPayload() payload}, and {@link #getMetaData() metadata}.
     *
     * @param delegate The {@link Message} delegate.
     */
    protected MessageDecorator(@Nonnull Message<P> delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getIdentifier() {
        return delegate.getIdentifier();
    }

    @Nonnull
    @Override
    public QualifiedName name() {
        return delegate.name();
    }

    @Override
    public MetaData getMetaData() {
        return delegate.getMetaData();
    }

    @Override
    public P getPayload() {
        return delegate.getPayload();
    }

    @Override
    public Class<P> getPayloadType() {
        return delegate.getPayloadType();
    }

    @Override
    public <S> SerializedObject<S> serializePayload(Serializer serializer, Class<S> expectedRepresentation) {
        return delegate.serializePayload(serializer, expectedRepresentation);
    }

    @Override
    public <S> SerializedObject<S> serializeMetaData(Serializer serializer, Class<S> expectedRepresentation) {
        return delegate.serializeMetaData(serializer, expectedRepresentation);
    }

    /**
     * Returns the wrapped message delegate.
     *
     * @return the delegate message
     */
    protected Message<P> getDelegate() {
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
     * @param stringBuilder the builder to append data to
     */
    protected void describeTo(StringBuilder stringBuilder) {
        stringBuilder.append("payload={")
                     .append(getPayload())
                     .append('}')
                     .append(", metadata={")
                     .append(getMetaData())
                     .append('}')
                     .append(", messageIdentifier='")
                     .append(getIdentifier())
                     .append('\'');
    }

    /**
     * Describe the type of message, used in {@link #toString()}.
     * <p>
     * Defaults to the simple class name of the actual instance.
     *
     * @return the type of message
     */
    protected String describeType() {
        return getClass().getSimpleName();
    }
}
