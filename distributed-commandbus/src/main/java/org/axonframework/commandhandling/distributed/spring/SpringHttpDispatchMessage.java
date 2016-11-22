package org.axonframework.commandhandling.distributed.spring;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.DispatchMessage;
import org.axonframework.serialization.Serializer;

import java.io.Serializable;

/**
 * Spring Http message that contains a CommandMessage that needs to be dispatched on a remote command bus segment.
 */
public class SpringHttpDispatchMessage extends DispatchMessage implements Serializable {

    /**
     * Initialize a SpringHttpDispatchMessage for the given {@code commandMessage}, to be serialized using given
     * {@code serializer}. {@code expectReply} indicates whether the sender will be expecting a reply.
     *
     * @param commandMessage The message to send to the remote segment
     * @param serializer     The serialize to serialize the message payload and metadata with
     * @param expectReply    whether or not the sender is waiting for a reply.
     */
    public SpringHttpDispatchMessage(CommandMessage<?> commandMessage, Serializer serializer, boolean expectReply) {
        super(commandMessage, serializer, expectReply);
    }

    @SuppressWarnings("unused")
    private SpringHttpDispatchMessage() {
        // Used for de-/serialization
    }

}
