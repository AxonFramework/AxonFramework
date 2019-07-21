package org.axonframework.queryhandling;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.updatestore.model.SubscriptionEntity;

import java.util.Optional;

public interface QueryUpdateStore {
    <Q, I, U> SubscriptionEntity<Q, I, U> createSubscription(SubscriptionId id, Q payload, ResponseType<I> responseType, ResponseType<U> updateResponseType);

    default <Q, I, U> SubscriptionEntity<Q, I, U> createSubscription(SubscriptionQueryMessage<Q, I, U> query) {
        return createSubscription(buildIdFromQuery(query), query.getPayload(), query.getResponseType(), query.getUpdateResponseType());
    }

    boolean subscriptionExists(SubscriptionId id);

    default boolean subscriptionExists(SubscriptionQueryMessage<?, ?, ?> query) {
        return subscriptionExists(buildIdFromQuery(query));
    }

    void removeSubscription(SubscriptionId id);

    default void removeSubscription(SubscriptionQueryMessage<?, ?, ?> query) {
        removeSubscription(buildIdFromQuery(query));
    }

    <Q, I, U> Iterable<SubscriptionEntity<Q, I, U>> getCurrentSubscriptions();

    <U> void postUpdate(SubscriptionEntity subscription, SubscriptionQueryUpdateMessage<U> update);

    <U> Optional<U> popUpdate(SubscriptionId subscriptionEntityId);


    SubscriptionId buildIdFromQuery(SubscriptionQueryMessage<?, ?, ?> query);

}
