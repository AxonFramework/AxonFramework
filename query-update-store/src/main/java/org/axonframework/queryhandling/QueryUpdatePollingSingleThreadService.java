package org.axonframework.queryhandling;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.common.Registration;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@SuppressWarnings("unchecked")
public class QueryUpdatePollingSingleThreadService implements QueryUpdatePollingService {

    private final List<PollingTuple> pollingSubscription = new CopyOnWriteArrayList<>();

    @Value("${axon.queryhandling.QueryUpdatePollingSingleThreadService.initialTryMillis:1}")
    private long initTryMillis;

    @Value("${axon.queryhandling.QueryUpdatePollingSingleThreadService.subsequentTryMillis:1}")
    private long subsequentTryMillis;

    @Resource
    private QueryUpdateStore queryUpdateStore;

    private final Thread poller = new Thread(() -> {
        Duration pause = Duration.ofMillis(subsequentTryMillis);

        while (true) {
            log.debug("polling for {} polling subscriptions.", pollingSubscription.size());

            Instant now = Instant.now();

            for (PollingTuple tuple : pollingSubscription) {
                if (tuple.getNextTry().isBefore(now)) {
                    peekAndSink(tuple.getId(), tuple.getSinkWrapper());
                    tuple.setNextTry(now.plus(pause));
                }
            }

            Thread.yield();
        }
    });

    @PostConstruct
    public void startPolling() {
        poller.start();
    }

    @SuppressWarnings("deprecation")
    @PreDestroy
    public void stopPolling() {
        poller.stop();
    }

    @Override
    public <U> Registration startPolling(SubscriptionId subscriptionId, FluxSinkWrapper<SubscriptionQueryUpdateMessage<U>> fluxSinkWrapper) {
        PollingTuple tuple = new PollingTuple(subscriptionId, fluxSinkWrapper);
        pollingSubscription.add(tuple);

        return () -> {
            log.debug("poll for {} removed", tuple.getId());
            pollingSubscription.remove(tuple);
            return true;
        };
    }

    private <U> void peekAndSink(
            SubscriptionId subscriptionId,
            FluxSinkWrapper<SubscriptionQueryUpdateMessage<U>> fluxSinkWrapper
    ) {
        Optional<U> peek = queryUpdateStore
                .popUpdate(subscriptionId);
        log.debug("Polled on {} got {}.", subscriptionId, peek);
        peek.ifPresent(u -> fluxSinkWrapper.next(asSubscriptionQueryUpdateMessage(u)));
    }

    private <U> SubscriptionQueryUpdateMessage<U> asSubscriptionQueryUpdateMessage(U payload) {
        return new GenericSubscriptionQueryUpdateMessage<>(payload);
    }

    @Data
    private class PollingTuple<U> {
        private final SubscriptionId id;
        private final FluxSinkWrapper<SubscriptionQueryUpdateMessage<U>> sinkWrapper;
        private Instant nextTry = Instant.now().plus(Duration.ofMillis(initTryMillis));
    }
}
