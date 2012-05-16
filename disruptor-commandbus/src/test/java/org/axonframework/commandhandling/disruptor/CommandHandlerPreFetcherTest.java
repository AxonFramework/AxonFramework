package org.axonframework.commandhandling.disruptor;

import net.sf.jsr107cache.Cache;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandTargetResolver;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.InterceptorChain;
import org.axonframework.commandhandling.VersionedAggregateIdentifier;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.eventstore.EventStore;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;
import org.mockito.*;

import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class CommandHandlerPreFetcherTest {

    private CommandHandlerInterceptor mockInvokerInterceptor;
    private CommandHandlerInterceptor mockPublisherInterceptor;
    private AggregateFactory<EventSourcedAggregateRoot> mockAggregateFactory;
    private Cache mockCache;
    private CommandTargetResolver mockCommandTargetResolver;
    private CommandHandler mockCommandHandler;
    private CommandHandlerPreFetcher<EventSourcedAggregateRoot> testSubject;
    private CommandMessage<Object> stubCommandMessage = new GenericCommandMessage<Object>(new Object());
    private String aggregateIdentifier = "aggregateId";
    private EventSourcedAggregateRoot mockAggregate;
    private EventStore mockEventStore;
    private GenericDomainEventMessage<Object> firstEvent;

    @Before
    public void setUp() throws Exception {
        mockCommandHandler = mock(CommandHandler.class);
        mockInvokerInterceptor = mock(CommandHandlerInterceptor.class);
        mockPublisherInterceptor = mock(CommandHandlerInterceptor.class);
        mockAggregateFactory = mock(AggregateFactory.class);
        mockCache = mock(Cache.class);
        mockCommandTargetResolver = mock(CommandTargetResolver.class);
        mockAggregate = mock(EventSourcedAggregateRoot.class);
        mockEventStore = mock(EventStore.class);
        testSubject = new CommandHandlerPreFetcher<EventSourcedAggregateRoot>(
                mockEventStore,
                mockAggregateFactory,
                Collections.<Class<?>, CommandHandler<?>>singletonMap(Object.class, mockCommandHandler),
                Collections.singletonList(mockInvokerInterceptor),
                Collections.singletonList(mockPublisherInterceptor),
                mockCommandTargetResolver, mockCache);

        when(mockCommandTargetResolver.resolveTarget(stubCommandMessage))
                .thenReturn(new VersionedAggregateIdentifier(aggregateIdentifier, null));
        when(mockAggregateFactory.createAggregate(any(), isA(DomainEventMessage.class))).thenReturn(mockAggregate);
        when(mockAggregate.getIdentifier()).thenReturn(aggregateIdentifier);
        firstEvent = new GenericDomainEventMessage<Object>(aggregateIdentifier, 1, new Object());
        when(mockEventStore.readEvents(Matchers.<String>any(), any()))
                .thenReturn(new SimpleDomainEventStream(
                        firstEvent));
    }

    @Test
    public void testInitializationOfInterceptorChains() throws Throwable {
        CommandHandlingEntry<EventSourcedAggregateRoot> entry = new CommandHandlingEntry<EventSourcedAggregateRoot>();
        BlacklistDetectingCallback mockCallback = mock(BlacklistDetectingCallback.class);
        entry.reset(stubCommandMessage, mockCallback);

        testSubject.onEvent(entry, 0, true);

        assertNotNull(entry.getUnitOfWork());
        assertTrue(entry.getUnitOfWork().isStarted());
        assertSame(mockAggregate, entry.getPreLoadedAggregate());

        entry.getInvocationInterceptorChain().proceed(stubCommandMessage);
        verify(mockInvokerInterceptor).handle(eq(stubCommandMessage), eq(entry.getUnitOfWork()),
                                              isA(InterceptorChain.class));
        verify(mockPublisherInterceptor, never()).handle(isA(CommandMessage.class), isA(UnitOfWork.class), isA(
                InterceptorChain.class));

        reset(mockInvokerInterceptor, mockPublisherInterceptor);
        entry.getPublisherInterceptorChain().proceed(stubCommandMessage);
        verify(mockPublisherInterceptor).handle(eq(stubCommandMessage), eq(entry.getUnitOfWork()),
                                                isA(InterceptorChain.class));
        verify(mockInvokerInterceptor, never()).handle(isA(CommandMessage.class), isA(UnitOfWork.class), isA(
                InterceptorChain.class));
    }

    @Test
    public void testAggregateInitialized_NotInCaches() throws Exception {
        CommandHandlingEntry<EventSourcedAggregateRoot> entry = new CommandHandlingEntry<EventSourcedAggregateRoot>();
        BlacklistDetectingCallback mockCallback = mock(BlacklistDetectingCallback.class);
        entry.reset(stubCommandMessage, mockCallback);

        testSubject.onEvent(entry, 0, true);

        verify(mockAggregateFactory).createAggregate(aggregateIdentifier, firstEvent);

        assertEquals(mockAggregate, entry.getUnitOfWork().getAggregate());
    }

    @Test
    public void testAggregateLoadedFromCache() throws Exception {
        CommandHandlingEntry<EventSourcedAggregateRoot> entry = new CommandHandlingEntry<EventSourcedAggregateRoot>();
        BlacklistDetectingCallback mockCallback = mock(BlacklistDetectingCallback.class);
        entry.reset(stubCommandMessage, mockCallback);

        when(mockCache.get(aggregateIdentifier)).thenReturn(mockAggregate);

        testSubject.onEvent(entry, 0, true);

        verify(mockAggregateFactory, never()).createAggregate(any(), any(DomainEventMessage.class));

        assertEquals(mockAggregate, entry.getUnitOfWork().getAggregate());
    }

    @Test
    public void testAggregateFoundInBuffer() throws Exception {
        CommandHandlingEntry<EventSourcedAggregateRoot> entry = new CommandHandlingEntry<EventSourcedAggregateRoot>();
        BlacklistDetectingCallback mockCallback = mock(BlacklistDetectingCallback.class);
        entry.reset(stubCommandMessage, mockCallback);

        when(mockCache.get(aggregateIdentifier)).thenReturn(mockAggregate);

        testSubject.onEvent(entry, 0, true);
        verify(mockCache, times(1)).get(any());

        testSubject.onEvent(entry, 1, true);

        verify(mockCache, times(1)).get(any());
        verify(mockAggregateFactory, never()).createAggregate(any(), any(DomainEventMessage.class));

        assertEquals(mockAggregate, entry.getUnitOfWork().getAggregate());
    }

    @Test
    public void testAggregateRemovedFromCacheOnRecovery() throws Exception {
        CommandHandlingEntry<EventSourcedAggregateRoot> entry = new CommandHandlingEntry<EventSourcedAggregateRoot>();
        BlacklistDetectingCallback mockCallback = mock(BlacklistDetectingCallback.class);

        entry.reset(stubCommandMessage, mockCallback);
        testSubject.onEvent(entry, 0, true);
        verify(mockAggregateFactory).createAggregate(aggregateIdentifier, firstEvent);

        entry.resetAsRecoverEntry(aggregateIdentifier);
        testSubject.onEvent(entry, 1, true);
        verify(mockCache).remove(aggregateIdentifier);
        verify(mockAggregateFactory).createAggregate(aggregateIdentifier, firstEvent);

        // the entry should be created from scratch
        entry.reset(stubCommandMessage, mockCallback);
        testSubject.onEvent(entry, 2, true);
        verify(mockAggregateFactory, times(2)).createAggregate(aggregateIdentifier, firstEvent);

        assertEquals(mockAggregate, entry.getUnitOfWork().getAggregate());
    }


}
