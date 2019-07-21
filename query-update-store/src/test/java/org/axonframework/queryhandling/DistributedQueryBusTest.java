package org.axonframework.queryhandling;

import demo.DemoQuery;
import demo.DemoQueryResult;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class DistributedQueryBusTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private SimpleQueryBus localQueryBus;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DistributedQueryUpdateEmitter queryUpdateEmitter;

    @InjectMocks
    private final QueryBus bus = new DistributedQueryBus();

    @Captor
    private ArgumentCaptor<SubscriptionQueryMessage<DemoQuery, DemoQueryResult, DemoQueryResult>> sqmCaptor;

    @Test
    public void testSubscription() {
        // arrange
        GenericSubscriptionQueryMessage<DemoQuery, DemoQueryResult, DemoQueryResult> sqm = new GenericSubscriptionQueryMessage<>(
                new DemoQuery("mockedAggId"),
                "mockedQueryName",
                ResponseTypes.instanceOf(DemoQueryResult.class),
                ResponseTypes.instanceOf(DemoQueryResult.class));

        // act
        bus.subscriptionQuery(sqm);

        // assert
        verify(localQueryBus, times(1))
                .subscriptionQuery(sqmCaptor.capture(), any(), anyInt());
        assertEquals(sqm, sqmCaptor.getValue());

        verify(queryUpdateEmitter, times(1))
                .registerUpdateHandler(sqmCaptor.capture(), any(), anyInt());
        assertEquals(sqm, sqmCaptor.getValue());
    }
}
