package io.axoniq.axonhub.client.util;

/**
 * Author: marc
 */
public class ExceptionSerializer {
    public static String serialize(String client, Throwable t) {
        StringBuilder builder = new StringBuilder(client).append(": ");
        builder.append(t.getMessage() == null ? t.getClass().getName() : t.getMessage());
        while(t.getCause() != null) {
            t = t.getCause();
            builder.append("\n").append(t.getMessage() == null ? t.getClass().getName() : t.getMessage());
        }
        return builder.toString();
    }

}
