package org.axonframework.unitofwork.nesting;

import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.Collections;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SimleListenerPublishingEventTest {

    private EventBus eventBus;
    private EventListener eventListener;

    @Before
    public void setUp() throws Exception {
        eventBus = new SimpleEventBus();
        eventListener = mock(EventListener.class);
        eventBus.subscribe(eventListener);
    }

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testEventPublishedByListenerIsHandled() {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        GenericEventMessage<Object> event = new GenericEventMessage<Object>("First",
                                                                            Collections.<String, Object>singletonMap(
                                                                                    "continue",
                                                                                    true));
        uow.publishEvent(event,
                         eventBus);
        verify(eventListener, never()).handle(isA(EventMessage.class));
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                CurrentUnitOfWork.get().publishEvent(new GenericEventMessage<String>("Second"), eventBus);
                return null;
            }
        }).when(eventListener).handle(event);
        uow.commit();
        verify(eventListener, times(2)).handle(isA(EventMessage.class));
    }
}
