package org.axonframework.commandhandling.distributed.spring;

import org.axonframework.commandhandling.distributed.ReplyMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.io.Serializable;

/**
 * Spring Http Message representing a reply to a dispatched command.
 */
public class SpringHttpReplyMessage<R> extends ReplyMessage implements Serializable {

    /**
     * Initialized a SpringHttpReplyMessage containing a reply to the command with given {commandIdentifier}, containing
     * either given {@code returnValue} or {@code error}, which uses the given {@code serializer} to
     * deserialize its contents.
     *
     * @param commandIdentifier The identifier of the command to which the message is a reply
     * @param returnValue       The return value of command process
     * @param error             The error that occuered during event processing. When provided (i.e. not
     *                          {@code null}, the given {@code returnValue} is ignored.
     * @param serializer        The serializer to serialize the message contents with
     */
    public SpringHttpReplyMessage(String commandIdentifier, Object returnValue, Throwable error, Serializer serializer) {
        super(commandIdentifier, returnValue, error, serializer);
    }

    @SuppressWarnings("unused")
    private SpringHttpReplyMessage() {
        // Used for de-/serialization
    }

    @Override
    public R getReturnValue(Serializer serializer) {
        if (!success || resultType == null) {
            return null;
        }

        SimpleSerializedObject<byte[]> serializedObject =
                new SimpleSerializedObject<>(serializedResult, byte[].class, resultType, resultRevision);
        return serializer.deserialize(serializedObject);
    }

}
