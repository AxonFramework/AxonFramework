package org.axonframework.queryhandling.benchmark;

import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.updatestore.SubscriberIdentityService;
import org.axonframework.spring.config.AxonConfiguration;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.UUID;
import java.util.function.Predicate;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

@TestConfiguration
public class MutedLocalSegmentTestConfig {

    @Bean("localQueryUpdateEmitter")
    public QueryUpdateEmitter localQueryUpdateEmitter(AxonConfiguration config) {
        MessageMonitor<? super SubscriptionQueryUpdateMessage<?>> updateMessageMonitor =
                config.messageMonitor(QueryUpdateEmitter.class, "queryUpdateEmitter");
        QueryUpdateEmitter spy = spy(SimpleQueryUpdateEmitter.builder()
                .updateMessageMonitor(updateMessageMonitor)
                .build());
        muteLocalQueryUpdateEmitter(spy);
        return spy;
    }

    @Bean
    public SubscriberIdentityService subscriberIdentityService() {
        return () -> UUID.randomUUID().toString();
    }

    private static <U> void muteLocalQueryUpdateEmitter(QueryUpdateEmitter spy) {
        doNothing()
                .when(spy)
                .emit(
                        Mockito.<Predicate<SubscriptionQueryMessage<?, ?, U>>>any(),
                        Mockito.<SubscriptionQueryUpdateMessage<U>>any()
                );
    }
}
