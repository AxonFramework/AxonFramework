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

package org.axonframework.commandhandling;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDecorator;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.QualifiedNameUtils;

import java.io.Serial;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nonnull;

/**
 * Generic implementation of the {@link CommandMessage} interface.
 *
 * @param <P> The type of {@link #getPayload() payload} contained in this {@link CommandMessage}.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 2.0.0
 */
public class GenericCommandMessage<P> extends MessageDecorator<P> implements CommandMessage<P> {

    @Serial
    private static final long serialVersionUID = 3282528436414939876L;

    private final String commandName;

    /**
     * Returns the given command as a {@link CommandMessage}. If {@code command} already implements
     * {@code CommandMessage}, it is returned as-is. When the {@code command} is another implementation of
     * {@link Message}, the {@link Message#getPayload()} and {@link Message#getMetaData()} are used as input for a new
     * {@link GenericCommandMessage}. Otherwise, the given {@code command} is wrapped into a
     * {@code GenericCommandMessage} as its payload.
     *
     * @param command The command to wrap as {@link CommandMessage}.
     * @return A {@link CommandMessage} containing given {@code command} as payload, a {@code command} if it already
     * implements {@code CommandMessage}, or a {@code CommandMessage} based on the result of
     * {@link Message#getPayload()} and {@link Message#getMetaData()} for other {@link Message} implementations.
     * @deprecated In favor of using the constructor, as we intend to enforce thinking about the
     * {@link QualifiedName name}.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public static <P> CommandMessage<P> asCommandMessage(@Nonnull Object command) {
        if (command instanceof CommandMessage) {
            return (CommandMessage<P>) command;
        } else if (command instanceof Message<?> message) {
            return new GenericCommandMessage<>(message.name(), (P) message.getPayload(), message.getMetaData());
        }
        return new GenericCommandMessage<>(QualifiedNameUtils.fromClassName(command.getClass()), (P) command,
                                           MetaData.emptyInstance());
    }

    /**
     * Constructs a {@link GenericCommandMessage} for the given {@code name} and {@code payload}.
     * <p>
     * The {@link MetaData} defaults to an empty instance.
     *
     * @param name    The {@link QualifiedName name} for this {@link CommandMessage}.
     * @param payload The payload of type {@code P} for this {@link CommandMessage}.
     */
    public GenericCommandMessage(@Nonnull QualifiedName name,
                                 @Nonnull P payload) {
        this(name, payload, MetaData.emptyInstance());
    }

    /**
     * Constructs a {@link GenericCommandMessage} for the given {@code name}, {@code payload}, and {@code metaData}.
     *
     * @param name     The {@link QualifiedName name} for this {@link CommandMessage}.
     * @param payload  The payload of type {@code P} for this {@link CommandMessage}.
     * @param metaData The metadata for this {@link CommandMessage}.
     */
    public GenericCommandMessage(@Nonnull QualifiedName name,
                                 @Nonnull P payload,
                                 @Nonnull Map<String, ?> metaData) {
        this(new GenericMessage<>(name, payload, metaData), payload.getClass().getName());
    }

    /**
     * Constructs a {@link GenericCommandMessage} with given {@code delegate} and {@code commandName}.
     * <p>
     * The {@code delegate} will be used supply the {@link Message#getPayload() payload}, {@link Message#name() name},
     * {@link Message#getMetaData() metadata} and {@link Message#getIdentifier() identifier} of the resulting
     * {@code GenericCommandMessage}.
     * <p>
     * Unlike the other constructors, this constructor will not attempt to retrieve any correlation data from the Unit
     * of Work.
     *
     * @param delegate    The {@link Message} containing {@link Message#getPayload() payload},
     *                    {@link Message#name() name}, {@link Message#getIdentifier() identifier} and
     *                    {@link Message#getMetaData() metadata} for the {@link CommandMessage} to reconstruct.
     * @param commandName The name for this {@link CommandMessage}.
     */
    public GenericCommandMessage(@Nonnull Message<P> delegate,
                                 @Nonnull String commandName) {
        super(delegate);
        this.commandName = commandName;
    }

    @Override
    public String getCommandName() {
        return commandName;
    }

    @Override
    public GenericCommandMessage<P> withMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericCommandMessage<>(getDelegate().withMetaData(metaData), commandName);
    }

    @Override
    public GenericCommandMessage<P> andMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericCommandMessage<>(getDelegate().andMetaData(metaData), commandName);
    }

    @Override
    public <C> CommandMessage<C> withConvertedPayload(@jakarta.annotation.Nonnull Function<P, C> conversion) {
        // TODO - Once Message declares a convert method, use that
        Message<P> delegate = getDelegate();
        Message<C> transformed = new GenericMessage<>(delegate.getIdentifier(),
                                                      delegate.name(),
                                                      conversion.apply(delegate.getPayload()),
                                                      delegate.getMetaData());
        return new GenericCommandMessage<>(transformed, commandName);
    }

    @Override
    protected void describeTo(StringBuilder stringBuilder) {
        super.describeTo(stringBuilder);
        stringBuilder.append(", commandName='")
                     .append(getCommandName())
                     .append('\'');
    }

    @Override
    protected String describeType() {
        return "GenericCommandMessage";
    }
}
