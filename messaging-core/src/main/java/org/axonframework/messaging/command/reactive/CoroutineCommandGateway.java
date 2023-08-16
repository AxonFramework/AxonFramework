package org.axonframework.messaging.command.reactive;

import kotlinx.coroutines.flow.Flow;

public interface CoroutineCommandGateway {

    <P> Flow<Void> dispatchFlow(P command);

    <P, R> Flow<R> dispatchFlowAndResult(P command);
}
