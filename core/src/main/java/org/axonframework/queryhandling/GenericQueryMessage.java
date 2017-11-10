package org.axonframework.queryhandling;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDecorator;
import org.axonframework.messaging.MetaData;

import java.util.Map;

/**
 * @author Marc Gathier
 * @since 3.1
 */
public class GenericQueryMessage<T> extends MessageDecorator<T> implements QueryMessage<T> {
    private final String queryName;
    private final String responseName;

    public GenericQueryMessage(T payload, String responseName) {
        this(payload, MetaData.emptyInstance(), payload.getClass().getName(), responseName);
    }

    public GenericQueryMessage(T payload, String queryName, String responseName) {
        this(payload, MetaData.emptyInstance(), queryName, responseName);
    }

    public GenericQueryMessage(T payload, Map<String, ?> metaData, String queryName, String responseName) {
        this(new GenericMessage<T>(payload, metaData), queryName, responseName);
    }
    public GenericQueryMessage(Message<T> delegate, String queryName, String responseName) {
        super(delegate);
        this.responseName = responseName;
        this.queryName = queryName;
    }

    @Override
    public String getQueryName() {
        return queryName;
    }

    @Override
    public String getResponseName() {
        return responseName;
    }


    @Override
    public QueryMessage<T> withMetaData(Map<String, ?> metaData) {
        return new GenericQueryMessage<T>(getDelegate().withMetaData(metaData), queryName, responseName);
    }

    @Override
    public QueryMessage<T> andMetaData(Map<String, ?> metaData) {
        return new GenericQueryMessage<T>(getDelegate().andMetaData(metaData), queryName, responseName);
    }
}
