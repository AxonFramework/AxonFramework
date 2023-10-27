package org.axonframework.query.messaging;

import java.util.stream.Stream;

public class DefaultQueryGateway implements QueryGateway {

    private final QueryBus queryBus;

    public DefaultQueryGateway(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @Override
    public <QP, QR> Stream<QR> stream(QP query) {
        return (Stream<QR>) queryBus.dispatch((QueryMessage) query).asStream();
    }
}
