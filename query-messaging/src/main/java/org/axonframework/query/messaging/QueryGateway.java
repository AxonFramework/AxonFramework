package org.axonframework.query.messaging;

import org.axonframework.messaging.MessageDispatcher;

import java.util.stream.Stream;

public interface QueryGateway extends MessageDispatcher<QueryMessage> {

    <QP, QR> Stream<QR> stream(QP query);
}
