package org.axonframework.query.messaging;

import org.axonframework.messaging.MessageDispatcher;
import org.reactivestreams.Publisher;

public interface ReactiveQueryGateway extends MessageDispatcher<QueryMessage> {

    <QP, QR> Publisher<QueryResponseMessage> stream(QueryMessage query);
}
