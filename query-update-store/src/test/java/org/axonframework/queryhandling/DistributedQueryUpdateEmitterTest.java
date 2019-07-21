package org.axonframework.queryhandling;

import demo.DemoQuery;
import demo.DemoQueryResult;
import org.axonframework.common.Registration;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.updatestore.model.SubscriptionEntity;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.FluxSink;
import reactor.util.concurrent.Queues;

import java.time.Duration;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class DistributedQueryUpdateEmitterTest {

    @Mock
    private QueryUpdateStore queryUpdateStore;

    @Mock
    private QueryUpdatePollingService queryUpdatePollingService;

    @Mock
    private SimpleQueryBus localSegment;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private SimpleQueryUpdateEmitter localQueryUpdateEmitter;

    @InjectMocks
    private final QueryUpdateEmitter queryUpdateEmitter = new DistributedQueryUpdateEmitter();

    @Mock
    private SubscriptionEntity subscriptionEntity;

    @Mock
    private Registration pollingRegistration;

    @Captor
    private ArgumentCaptor<SubscriptionQueryMessage<DemoQuery, DemoQueryResult, DemoQueryResult>> sqmCaptor;

    @Captor
    private ArgumentCaptor<FluxSinkWrapper<SubscriptionQueryUpdateMessage<DemoQueryResult>>> fswCaptor;

    @Before
    public void mockQueryUpdateStore() {
        doReturn(subscriptionEntity)
                .when(queryUpdateStore)
                .createSubscription(any(SubscriptionQueryMessage.class));
    }

    @Before
    public void mockPollingFuture() {
        doReturn(pollingRegistration)
                .when(queryUpdatePollingService)
                .startPolling(any(), any());
    }

    @Before
    public void mockLocalSegment() {
        doReturn(localQueryUpdateEmitter).when(localSegment).queryUpdateEmitter();
    }

    @Test
    public void testRegistration() {
        // arrange
        SubscriptionQueryMessage<DemoQuery, DemoQueryResult, DemoQueryResult> sqm =
                new GenericSubscriptionQueryMessage<>(
                        new DemoQuery("mockedAggId"),
                        ResponseTypes.instanceOf(DemoQueryResult.class),
                        ResponseTypes.instanceOf(DemoQueryResult.class)
                );

        // act
        UpdateHandlerRegistration<DemoQueryResult> reg =
                queryUpdateEmitter.registerUpdateHandler(
                        sqm,
                        SubscriptionQueryBackpressure.defaultBackpressure(),
                        Queues.SMALL_BUFFER_SIZE);

        // assert
        verify(queryUpdateStore, times(1))
                .createSubscription(sqm);

        verify(queryUpdatePollingService, times(1))
                .startPolling(any(), any());

        verify(localQueryUpdateEmitter, times(1))
                .registerUpdateHandler(sqmCaptor.capture(), any(), anyInt());
        assertEquals(sqm, sqmCaptor.getValue());

        try {
            reg.getUpdates().blockFirst(Duration.ZERO);
            fail("Expected no updates.");
        } catch (IllegalStateException ex) {
            // okay
        }
    }

    @Test
    public void testCancellation() {
        // arrange
        SubscriptionQueryMessage<DemoQuery, DemoQueryResult, DemoQueryResult> sqm =
                new GenericSubscriptionQueryMessage<>(
                        new DemoQuery("mockedAggId"),
                        ResponseTypes.instanceOf(DemoQueryResult.class),
                        ResponseTypes.instanceOf(DemoQueryResult.class)
                );

        doReturn(Boolean.TRUE).when(pollingRegistration).cancel();
        when(localQueryUpdateEmitter
                .registerUpdateHandler(any(), any(), anyInt())
                .getRegistration()
                .cancel()).thenReturn(Boolean.TRUE);

        // act
        UpdateHandlerRegistration<DemoQueryResult> reg =
                queryUpdateEmitter.registerUpdateHandler(
                        sqm,
                        SubscriptionQueryBackpressure.defaultBackpressure(),
                        Queues.SMALL_BUFFER_SIZE);
        boolean cancelled = reg.getRegistration().cancel();

        // assert
        assertTrue(cancelled);

        verify(pollingRegistration, times(1))
                .cancel();

        verify(localQueryUpdateEmitter
                .registerUpdateHandler(any(), any(), anyInt())
                .getRegistration(), times(1))
                .cancel();
    }

    @Test
    public void testLocalUpdate() {
        // arrange
        SubscriptionQueryMessage<DemoQuery, DemoQueryResult, DemoQueryResult> sqm =
                new GenericSubscriptionQueryMessage<>(
                        new DemoQuery("mockedAggId"),
                        ResponseTypes.instanceOf(DemoQueryResult.class),
                        ResponseTypes.instanceOf(DemoQueryResult.class)
                );

        EmitterProcessor<SubscriptionQueryUpdateMessage<?>> processor =
                EmitterProcessor.create(Queues.SMALL_BUFFER_SIZE);
        FluxSink<SubscriptionQueryUpdateMessage<?>> sink =
                processor.sink(
                        SubscriptionQueryBackpressure
                                .defaultBackpressure()
                                .getOverflowStrategy());

        UpdateHandlerRegistration<Object> localUpdateHandlerRegistration = new UpdateHandlerRegistration(
                () -> false,
                processor.replay(Queues.SMALL_BUFFER_SIZE).autoConnect());

        when(localQueryUpdateEmitter.registerUpdateHandler(any(), any(), anyInt()))
                .thenReturn(localUpdateHandlerRegistration);

        // act
        UpdateHandlerRegistration<DemoQueryResult> reg =
                queryUpdateEmitter.registerUpdateHandler(
                        sqm,
                        SubscriptionQueryBackpressure.defaultBackpressure(),
                        Queues.SMALL_BUFFER_SIZE);

        sink.next(new GenericSubscriptionQueryUpdateMessage<>(
                DemoQueryResult.class,
                new DemoQueryResult("mockedAggId"))
        );

        // assert
        SubscriptionQueryUpdateMessage<DemoQueryResult> first = reg.getUpdates().blockFirst();
        assertEquals("mockedAggId", first.getPayload().getAggId());
    }

    @Test
    public void testRemoteUpdate() {
        // arrange
        SubscriptionQueryMessage<DemoQuery, DemoQueryResult, DemoQueryResult> sqm =
                new GenericSubscriptionQueryMessage<>(
                        new DemoQuery("mockedAggId"),
                        ResponseTypes.instanceOf(DemoQueryResult.class),
                        ResponseTypes.instanceOf(DemoQueryResult.class)
                );

        // act
        UpdateHandlerRegistration<DemoQueryResult> reg =
                queryUpdateEmitter.registerUpdateHandler(
                        sqm,
                        SubscriptionQueryBackpressure.defaultBackpressure(),
                        Queues.SMALL_BUFFER_SIZE);

        verify(queryUpdatePollingService, times(1))
                .startPolling(any(), fswCaptor.capture());

        fswCaptor.getValue().next(new GenericSubscriptionQueryUpdateMessage<>(
                DemoQueryResult.class,
                new DemoQueryResult("mockedAggId"))
        );

        // assert
        SubscriptionQueryUpdateMessage<DemoQueryResult> first = reg.getUpdates().blockFirst();
        assertEquals("mockedAggId", first.getPayload().getAggId());
    }
}
