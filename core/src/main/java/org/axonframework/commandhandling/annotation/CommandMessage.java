package org.axonframework.commandhandling.annotation;

import org.axonframework.domain.Message;
import org.axonframework.domain.MetaData;

/**
 * Currently an Axon-internal class. The concept of CommandMessage will soon be available in the public API.
 *
 * @author Allard Buijze
 * @since 2.0
 * @deprecated Not public yet.
 */
@Deprecated
public class CommandMessage implements Message {
    private final Object command;

    public CommandMessage(Object command) {
        this.command = command;
    }

    @Override
    public MetaData getMetaData() {
        return MetaData.emptyInstance();
    }

    @Override
    public Object getPayload() {
        return command;
    }

    @Override
    public Class getPayloadType() {
        return command.getClass();
    }
}
