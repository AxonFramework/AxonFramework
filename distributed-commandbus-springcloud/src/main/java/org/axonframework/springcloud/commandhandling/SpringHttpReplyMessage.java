package org.axonframework.springcloud.commandhandling;

import org.axonframework.commandhandling.distributed.ReplyMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.io.Serializable;

/**
 * Spring Http Message representing a reply to a dispatched command.
 */
public class SpringHttpReplyMessage<R> extends ReplyMessage implements Serializable {

    /**
     * Initializes a SpringHttpReplyMessage containing a reply to the command with given {commandIdentifier} and given
     * {@code returnValue}. The parameter {@code success} determines whether the was executed successfully or not.
     *
     * @param commandIdentifier The identifier of the command to which the message is a reply
     * @param success           Whether or not the command executed successfully or not
     * @param returnValue       The return value of command process
     *                          the given {@code returnValue} is ignored.
     * @param serializer        The serializer to serialize the message contents with
     */
    public SpringHttpReplyMessage(String commandIdentifier, boolean success, Object returnValue,
                                  Serializer serializer) {
        super(commandIdentifier, success, returnValue, serializer);
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
