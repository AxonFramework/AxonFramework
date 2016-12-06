package org.axonframework.springcloud.commandhandling;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.distributed.DispatchMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SerializedMetaData;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.io.Serializable;

/**
 * Spring Http message that contains a CommandMessage that needs to be dispatched on a remote command bus segment.
 */
public class SpringHttpDispatchMessage<C> extends DispatchMessage implements Serializable {

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

    @Override
    public CommandMessage<C> getCommandMessage(Serializer serializer) {
        SimpleSerializedObject<byte[]> serializedPayload =
                new SimpleSerializedObject<>(this.serializedPayload, byte[].class, payloadType, payloadRevision);
        SerializedMetaData<byte[]> serializedMetaData = new SerializedMetaData<>(this.serializedMetaData, byte[].class);
        final MetaData metaData = serializer.deserialize(serializedMetaData);
        GenericMessage<C> genericMessage =
                new GenericMessage<>(commandIdentifier, serializer.deserialize(serializedPayload), metaData);
        return new GenericCommandMessage<>(genericMessage, commandName);
    }
}
