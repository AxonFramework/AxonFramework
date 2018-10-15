package org.axonframework.axonserver.connector.query;

import io.axoniq.axonserver.grpc.ErrorMessage;
import org.axonframework.messaging.RemoteExceptionDescription;
import org.axonframework.messaging.RemoteHandlingException;

/**
 * Author: marc
 */
public class RemoteQueryException extends RemoteHandlingException {
    private final String errorCode;
    private final String server;

    public RemoteQueryException(String errorCode, ErrorMessage message) {
        super(new RemoteExceptionDescription(message.getDetailsList()));

        this.errorCode = errorCode;
        this.server = message.getLocation();
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getServer() {
        return server;
    }

    @Override
    public String toString() {
        return "RemoteQueryException{" +
                "message=" + getMessage() +
                ", errorCode='" + errorCode + '\'' +
                ", location='" + server + '\'' +
                '}';
    }

}
