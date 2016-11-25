package org.axonframework.commandhandling.distributed;

import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.util.Arrays;
import java.util.Objects;

/**
 * Base class for reply messages which may be used in the {@link CommandBusConnector} for replying on received
 * commands from other nodes.
 */
public abstract class ReplyMessage {

    protected String commandIdentifier;
    protected boolean success;
    protected String resultType;
    protected String resultRevision;
    protected byte[] serializedResult;

    /**
     * Default constructor required for de-/serialization of extending classes. Do not use directly.
     */
    protected ReplyMessage() {
    }

    /**
     * Initialized a ReplyMessage containing a reply to the command with given {commandIdentifier}, containing
     * either given {@code returnValue} or {@code error}, which uses the given {@code serializer} to
     * deserialize its contents.
     *
     * @param commandIdentifier The identifier of the command to which the message is a reply
     * @param returnValue       The return value of command process
     * @param error             The error that occuered during event processing. When provided (i.e. not
     *                          {@code null}, the given {@code returnValue} is ignored.
     * @param serializer        The serializer to serialize the message contents with
     */
    protected ReplyMessage(String commandIdentifier, Object returnValue, Throwable error, Serializer serializer) {
        this.success = error == null;
        SerializedObject<byte[]> result;
        if (success) {
            if (returnValue == null) {
                result = null;
            } else {
                result = serializer.serialize(returnValue, byte[].class);
            }
        } else {
            result = serializer.serialize(error, byte[].class);
        }
        this.commandIdentifier = commandIdentifier;
        if (result != null) {
            this.resultType = result.getType().getName();
            this.resultRevision = result.getType().getRevision();
            this.serializedResult = result.getData();
        }
    }

    /**
     * Returns the returnValue of the command processing. If {@link #isSuccess()} return {@code false}, this
     * method returns {@code null}. This method also returns {@code null} if response processing returned
     * a {@code null} value.
     *
     * @param serializer The serializer to deserialize the result with
     * @return The return value of command processing
     */
    public Object getReturnValue(Serializer serializer) {
        if (!success || resultType == null) {
            return null;
        }
        return deserializeResult(serializer);
    }

    /**
     * Returns the error of the command processing. If {@link #isSuccess()} return {@code true}, this
     * method returns {@code null}.
     *
     * @param serializer The serializer to deserialize the result with
     * @return The exception thrown during command processing
     */
    public Throwable getError(Serializer serializer) {
        if (success) {
            return null;
        }
        return (Throwable) deserializeResult(serializer);
    }

    private Object deserializeResult(Serializer serializer) {
        return serializer.deserialize(new SimpleSerializedObject<>(serializedResult, byte[].class,
                resultType, resultRevision));
    }

    /**
     * Returns the identifier of the command for which this message is a reply.
     *
     * @return the identifier of the command for which this message is a reply as a {@code String}
     */
    public String getCommandIdentifier() {
        return commandIdentifier;
    }

    /**
     * Whether the reply message represents a successfully executed command. In this case, successful means that the
     * command's execution did not result in an exception.
     *
     * @return {@code true} if this reply contains a return value, {@code false} if it contains an error.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the result type of the serialized result for which this message is a reply.
     *
     * @return the result type of the serialized result for which this message is a reply as a {@code String}
     */
    public String getResultType() {
        return resultType;
    }

    /**
     * Returns the result revision of the serialized result for which this message is a reply.
     *
     * @return the result revision of the serialized result for which this message is a reply as a {@code String}
     */
    public String getResultRevision() {
        return resultRevision;
    }

    /**
     * Returns the serialized result for which this message is a reply.
     *
     * @return the serialized result for which this message is a reply as a {@code byte[]}
     */
    public byte[] getSerializedResult() {
        return serializedResult;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReplyMessage that = (ReplyMessage) o;
        return success == that.success &&
                Objects.equals(commandIdentifier, that.commandIdentifier) &&
                Objects.equals(resultType, that.resultType) &&
                Objects.equals(resultRevision, that.resultRevision) &&
                Arrays.equals(serializedResult, that.serializedResult);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commandIdentifier, success, resultType, resultRevision, serializedResult);
    }

    @Override
    public String toString() {
        return "ReplyMessage{" +
                "commandIdentifier='" + commandIdentifier + '\'' +
                ", success=" + success +
                ", resultType='" + resultType + '\'' +
                ", resultRevision='" + resultRevision + '\'' +
                ", serializedResult=" + Arrays.toString(serializedResult) +
                '}';
    }

}
