package org.axonframework.axonserver.connector.util;

import io.axoniq.axonserver.grpc.ErrorMessage;

/**
 * Author: marc
 */
public class ExceptionSerializer {
    public static ErrorMessage serialize(String client, Throwable t) {
        if( t.getCause() != null)
            t = t.getCause();
        ErrorMessage.Builder builder = ErrorMessage.newBuilder().setLocation(client).setMessage(
                t.getMessage() == null ? t.getClass().getName() : t.getMessage());
        builder.addDetails(t.getMessage() == null ? t.getClass().getName() : t.getMessage());
        while(t.getCause() != null) {
            t = t.getCause();
            builder.addDetails(t.getMessage() == null ? t.getClass().getName() : t.getMessage());
        }
        return builder.build();
    }

}
