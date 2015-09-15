package org.axonframework.commandhandling.disruptor;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.RollbackConfiguration;
import org.axonframework.commandhandling.RollbackOnAllExceptionsConfiguration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;
import org.axonframework.eventstore.EventStore;
import org.axonframework.testutils.MockException;
import org.axonframework.unitofwork.SaveAggregateCallback;
import org.axonframework.unitofwork.TransactionManager;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListener;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.concurrent.Executor;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.isA;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Rene de Waele
 */
public class EventPublisherTest {

    private EventPublisher subject;
    private CommandHandlingEntry commandHandlingEntry;
    private RollbackConfiguration rollbackConfiguration;
    private EventBus mockEventBus;

    @Before
    public void setUp() throws Exception {
        mockEventBus = mock(EventBus.class);
        rollbackConfiguration = spy(new RollbackOnAllExceptionsConfiguration());
        subject = new EventPublisher(mock(EventStore.class), mockEventBus, mock(Executor.class),
                                     mock(TransactionManager.class), rollbackConfiguration, 0);
        commandHandlingEntry = new CommandHandlingEntry(false);
        commandHandlingEntry.reset(mock(CommandMessage.class), mock(CommandHandler.class), 0, 0, 0, null,
                Collections.<CommandHandlerInterceptor>emptyList(),
                Collections.<CommandHandlerInterceptor>emptyList());
    }

    @Test
    public void testBlacklistAggregateOnRollback() throws Exception {
        Throwable mockException = new MockException();
        DisruptorUnitOfWork unitOfWork = commandHandlingEntry.getUnitOfWork();
        unitOfWork.setAggregateType("aggregateType");
        EventSourcedAggregateRoot mockAggregate = mock(EventSourcedAggregateRoot.class);
        when(mockAggregate.getIdentifier()).thenReturn("aggregateId");
        SaveAggregateCallback mockSaveAggregateCallback = mock(SaveAggregateCallback.class);
        unitOfWork.registerAggregate(mockAggregate, mockEventBus, mockSaveAggregateCallback);
        UnitOfWorkListener unitOfWorkListener = mock(UnitOfWorkListener.class);
        unitOfWork.registerListener(unitOfWorkListener);

        commandHandlingEntry.setExceptionResult(mockException);

        subject.onEvent(commandHandlingEntry, 0, true);
        verify(rollbackConfiguration).rollBackOn(mockException);
        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(unitOfWorkListener, atLeastOnce()).onRollback(any(UnitOfWork.class), exceptionCaptor.capture());
        assertThat(exceptionCaptor.getAllValues(), hasItem(isA(AggregateBlacklistedException.class)));
    }
}