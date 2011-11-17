package org.axonframework.commandhandling.annotation;

import org.axonframework.domain.Message;
import org.axonframework.domain.MetaData;

import java.util.Map;

/**
 * Currently an Axon-internal class. The concept of CommandMessage will soon be available in the public API.
 *
 * @param <T> The type of payload contained in this Message
 * @author Allard Buijze
 * @since 2.0
 * @deprecated Not public yet.
 */
@Deprecated
public class CommandMessage<T> implements Message<T> {

    private final T command;

    /**
     * Create a CommandMessage with the given <code>command</code> as payload.
     *
     * @param command the payload for the Message
     */
    public CommandMessage(T command) {
        this.command = command;
    }

    @Override
    public MetaData getMetaData() {
        return MetaData.emptyInstance();
    }

    @Override
    public T getPayload() {
        return command;
    }

    @Override
    public Class getPayloadType() {
        return command.getClass();
    }

    @Override
    public CommandMessage<T> withMetaData(Map<String, Object> metaData) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public CommandMessage<T> andMetaData(Map<String, Object> metaData) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
