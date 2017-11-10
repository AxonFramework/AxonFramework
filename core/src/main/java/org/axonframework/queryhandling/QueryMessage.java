package org.axonframework.queryhandling;

import org.axonframework.messaging.Message;

import java.util.Map;

/**
 * @author Marc Gathier
 * @since 3.1
 */
public interface QueryMessage<T>  extends Message<T> {
    String getQueryName();
    String getResponseName();

    QueryMessage<T> withMetaData(Map<String, ?> var1);

    QueryMessage<T> andMetaData(Map<String, ?> var1);

}
