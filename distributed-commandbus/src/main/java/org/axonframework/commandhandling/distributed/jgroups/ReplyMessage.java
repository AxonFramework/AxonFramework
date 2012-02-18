package org.axonframework.commandhandling.distributed.jgroups;

import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;
import org.jgroups.util.Streamable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * JGroups Message representing a reply to a dispatched command.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ReplyMessage implements Streamable, Externalizable {

    private static final long serialVersionUID = 6955710928767199410L;

    private String commandIdentifier;
    private boolean success;
    private String resultType;
    private String resultRevision;
    private byte[] serializedResult;

    /**
     * Default constructor required by the {@link Streamable} and {@link Externalizable} interfaces. Do not use
     * directly.
     */
    @SuppressWarnings("UnusedDeclaration")
    public ReplyMessage() {
    }

    /**
     * Constructs a message containing a reply to the command with given <code>commandIdentifier</code>, containing
     * either given <code>returnValue</code> or <code>error</code>, which uses the given <code>serializer</code> to
     * deserialize its contents.
     *
     * @param commandIdentifier The identifier of the command to which the message is a reply
     * @param returnValue       The return value of command process
     * @param error             The error that occuered during event processing. When provided (i.e. not
     *                          <code>null</code>, the given <code>returnValue</code> is ignored.
     * @param serializer        The serializer to serialize the message contents with
     */
    public ReplyMessage(String commandIdentifier, Object returnValue, Throwable error, Serializer serializer) {
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
     * Whether the reply message represents a successfully executed command. In this case, successful means that the
     * command's execution did not result in an exception.
     *
     * @return <code>true</code> if this reply contains a return value, <code>false</code> if it contains an error.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the returnValue of the command processing. If {@link #isSuccess()} return <code>false</code>, this
     * method returns <code>null</code>. This method also returns <code>null</code> if response processing returned
     * a <code>null</code> value.
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
     * Returns the error of the command processing. If {@link #isSuccess()} return <code>true</code>, this
     * method returns <code>null</code>.
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
        return serializer.deserialize(new SimpleSerializedObject<byte[]>(serializedResult, byte[].class,
                                                                         resultType, resultRevision));
    }

    /**
     * Returns the identifier of the command for which this message is a reply.
     *
     * @return the identifier of the command for which this message is a reply
     */
    public String getCommandIdentifier() {
        return commandIdentifier;
    }

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeUTF(commandIdentifier);
        out.writeBoolean(success);
        if (resultType == null) {
            out.writeUTF("_null");
        } else {
            out.writeUTF(resultType);
            out.writeUTF(resultRevision == null ? "_null" : resultRevision);
            out.writeInt(serializedResult.length);
            out.write(serializedResult);
        }
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        commandIdentifier = in.readUTF();
        success = in.readBoolean();
        resultType = in.readUTF();
        if ("_null".equals(resultType)) {
            resultType = null;
        } else {
            resultRevision = in.readUTF();
            if ("_null".equals(resultRevision)) {
                resultRevision = null;
            }
            serializedResult = new byte[in.readInt()];
            in.readFully(serializedResult);
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        writeTo(out);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException {
        readFrom(in);
    }
}
