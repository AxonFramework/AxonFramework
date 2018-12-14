package org.axonframework.axonserver.connector.query;

import io.axoniq.axonserver.grpc.ErrorMessage;
import org.axonframework.messaging.RemoteExceptionDescription;
import org.axonframework.messaging.RemoteHandlingException;

/**
 * An AxonServer Exception which is thrown on a Query Handling exception.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class AxonServerRemoteQueryHandlingException extends RemoteHandlingException {

    private static final long serialVersionUID = -8868624888839585045L;

    private final String errorCode;
    private final String server;

    /**
     * Initialize a Query Handling exception from a remote source.
     *
     * @param errorCode a {@link String} defining the error code of this exception
     * @param message   an {@link ErrorMessage} describing the exception
     */
    public AxonServerRemoteQueryHandlingException(String errorCode, ErrorMessage message) {
        super(new RemoteExceptionDescription(message.getDetailsList()));
        this.errorCode = errorCode;
        this.server = message.getLocation();
    }

    /**
     * Return a {@link String} defining the error code.
     *
     * @return a {@link String} defining the error code
     */
    public String getErrorCode() {
        return errorCode;
    }


    /**
     * Return a {@link String} defining the location where the error originated.
     *
     * @return a {@link String} defining the location where the error originated
     */
    public String getServer() {
        return server;
    }

    @Override
    public String toString() {
        return "AxonServerRemoteQueryHandlingException{" +
                "message=" + getMessage() +
                ", errorCode='" + errorCode + '\'' +
                ", location='" + server + '\'' +
                '}';
    }
}
