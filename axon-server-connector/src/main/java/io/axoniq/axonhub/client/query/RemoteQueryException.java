package io.axoniq.axonhub.client.query;

import io.axoniq.axonhub.ErrorMessage;

/**
 * Author: marc
 */
public class RemoteQueryException extends Throwable {
    private final String errorCode;
    private final String location;
    private final Iterable<String> details;

    public RemoteQueryException(String errorCode, ErrorMessage message) {
        super(message.getMessage());

        details = message.getDetailsList();
        this.errorCode = errorCode;
        location = message.getLocation();
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getLocation() {
        return location;
    }

    public Iterable<String> getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return "RemoteQueryException{" +
                "message=" + getMessage() +
                ", errorCode='" + errorCode + '\'' +
                ", location='" + location + '\'' +
                ", details=" + details +
                '}';
    }

}
