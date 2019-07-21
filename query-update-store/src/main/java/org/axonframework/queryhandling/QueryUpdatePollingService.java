package org.axonframework.queryhandling;

import org.axonframework.common.Registration;

public interface QueryUpdatePollingService {

    <U> Registration startPolling(
            SubscriptionId subscriptionId,
            FluxSinkWrapper<SubscriptionQueryUpdateMessage<U>> fluxSinkWrapper
    );
}
