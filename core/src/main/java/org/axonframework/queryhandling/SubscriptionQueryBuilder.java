package org.axonframework.queryhandling;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.queryhandling.responsetypes.ResponseType;
import org.axonframework.queryhandling.responsetypes.ResponseTypes;
import reactor.util.concurrent.Queues;

/**
 * Usage:
 *
 * <code>
 *   queryGateway.createSubscriptionQuery()
 *   .query(new Query("foo"))
 *   .responseType(Result.class)
 *   .subscribe();
 * </code>
 *
 * @param <Q>
 * @param <I>
 * @param <U>
 */
public class SubscriptionQueryBuilder<Q, I, U> {

  private final QueryBus queryBus;
  private final MessageDispatchInterceptor<? super QueryMessage<?, ?>>[] dispatchInterceptors;

   SubscriptionQueryBuilder(QueryBus queryBus,
      MessageDispatchInterceptor<? super QueryMessage<?, ?>>[] dispatchInterceptors) {
    this.queryBus = queryBus;
    this.dispatchInterceptors = dispatchInterceptors;
  }

  private String queryName;
  private Q query;

  private ResponseType<I> initialResponseType;
  private ResponseType<U> updateResponseType;

  private SubscriptionQueryBackpressure backpressure = SubscriptionQueryBackpressure
      .defaultBackpressure();
  private int bufferSize = Queues.SMALL_BUFFER_SIZE;


  public <X> SubscriptionQueryBuilder<X, I, U> query(X query) {
    SubscriptionQueryBuilder<X, I,U> b = new SubscriptionQueryBuilder<>(queryBus, dispatchInterceptors);
    b.query = query;
    b.initialResponseType = this.initialResponseType;
    b.updateResponseType=this.updateResponseType;
    return b;
  }

  private <XQ, XI,XU>SubscriptionQueryBuilder<XQ, XI,XU> copy() {
    SubscriptionQueryBuilder<XQ, XI,XU> b =  new SubscriptionQueryBuilder<>(queryBus, dispatchInterceptors);
    b.queryName = this.queryName;
    b.backpressure = this.backpressure;
    b.bufferSize = this.bufferSize;

    return b;
  }

  public SubscriptionQueryBuilder<Q,I,U> queryName(String queryName) {
    this.queryName = queryName;
    return this;
  }
  public SubscriptionQueryBuilder<Q,I,U> backpressure(SubscriptionQueryBackpressure backpressure) {
    this.backpressure = backpressure;
    return this;
  }
  public SubscriptionQueryBuilder<Q,I,U> bufferSize(int bufferSize) {
    this.bufferSize = bufferSize;
    return this;
  }

  public <X> SubscriptionQueryBuilder<Q,X,X> responseType(Class<X> responseType) {
    return this.responseType(ResponseTypes.instanceOf(responseType));
  }

  public <X> SubscriptionQueryBuilder<Q,X,X> responseType(ResponseType<X> responseType) {
    SubscriptionQueryBuilder<Q, X,X> b = this.copy();
    b.query = this.query;
    b.initialResponseType = responseType;
    b.updateResponseType = responseType;

    return b;
  }

  public <X> SubscriptionQueryBuilder<Q, X, U> initialResponseType(Class<X> responseType ) {
    return this.initialResponseType(ResponseTypes.instanceOf(responseType));
  }

  public <X> SubscriptionQueryBuilder<Q, X, U> initialResponseType(ResponseType<X> responseType ) {
    SubscriptionQueryBuilder<Q, X,U> b = this.copy();
    b.query = this.query;
    b.initialResponseType = responseType;
    b.updateResponseType = this.updateResponseType;

    return b;
  }

  public <X> SubscriptionQueryBuilder<Q, I, X> updateResponseType(Class<X>responseType ) {
    return this.updateResponseType(ResponseTypes.instanceOf(responseType));
  }

  public <X> SubscriptionQueryBuilder<Q, I, X> updateResponseType(ResponseType<X>responseType ) {
    SubscriptionQueryBuilder<Q, I,X> b = this.copy();
    b.query = this.query;
    b.initialResponseType = this.initialResponseType;
    b.updateResponseType = responseType;

    return b;
  }

  private void init() {
    requireNonNull(query, "Query must not be null!");
    requireNonNull(initialResponseType, "Initial responseType must not be null!");
    requireNonNull(updateResponseType, "Update responseType must not be null!");
    requireNonNull(query, "Query must not be null!");

    this.queryName = Optional.ofNullable(queryName).orElse(query.getClass().getName());
  }

  public SubscriptionQueryResult<I, U> subscribe() {
    init();

    final SubscriptionQueryMessage<Q, I, U> subscriptionQueryMessage =
        new GenericSubscriptionQueryMessage<Q,I,U>(query, queryName, initialResponseType, updateResponseType);

    final SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> result = queryBus
        .subscriptionQuery(processInterceptors(subscriptionQueryMessage), backpressure, bufferSize);

    return new DefaultSubscriptionQueryResult<>(
        result.initialResult().map(QueryResponseMessage::getPayload),
        result.updates().map(SubscriptionQueryUpdateMessage::getPayload),
        result);
  }

  @SuppressWarnings("unchecked")
  private <Q, R, T extends QueryMessage<Q, R>> T processInterceptors(T query) {
    T intercepted = query;
    for (MessageDispatchInterceptor<? super QueryMessage<?, ?>> interceptor : dispatchInterceptors) {
      intercepted = (T) interceptor.handle(intercepted);
    }
    return intercepted;
  }
}
