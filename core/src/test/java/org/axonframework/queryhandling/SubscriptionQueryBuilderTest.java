package org.axonframework.queryhandling;

import static org.mockito.Mockito.mock;

import java.io.Serializable;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.SubscriptionQueryBuilder;
import org.axonframework.queryhandling.SubscriptionQueryBackpressure;
import org.axonframework.queryhandling.responsetypes.ResponseTypes;
import org.junit.Test;
import reactor.core.publisher.FluxSink.OverflowStrategy;

public class SubscriptionQueryBuilderTest {

  public static class QueryType implements Serializable {

    private final String name;

    public QueryType(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }
  public static class InitialResponse implements Serializable {

    private final String name;

    public InitialResponse(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }
  public static class UpdateResponse implements Serializable {

    private final String name;

    public UpdateResponse(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  private final QueryBus queryBus = mock(QueryBus.class);
  private final MessageDispatchInterceptor[] dispatchInterceptors = new MessageDispatchInterceptor[0];


  @Test
  public void name() {
    SubscriptionQueryBuilder<QueryType, InitialResponse, UpdateResponse> builder = new SubscriptionQueryBuilder<>(queryBus, dispatchInterceptors)
        .query(new QueryType("foo"))
        .queryName("someQueryName")
        .initialResponseType(ResponseTypes.instanceOf(InitialResponse.class))
        .updateResponseType(ResponseTypes.instanceOf(UpdateResponse.class))
        .bufferSize(10)
        .backpressure(new SubscriptionQueryBackpressure(OverflowStrategy.BUFFER));

    System.out.println(builder.toString());
  }
}