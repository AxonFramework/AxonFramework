package org.axonframework.spring.config;

import org.axonframework.common.Registration;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandlerAdapter;
import org.axonframework.spring.config.event.CommandHandlersSubscribedEvent;
import org.axonframework.spring.config.event.QueryHandlersSubscribedEvent;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link QueryHandlerSubscriber}.
 *
 * @author Steven van Beelen
 */
class QueryHandlerSubscriberTest {

    private final ApplicationContext applicationContext = mock(ApplicationContext.class);
    private final QueryBus queryBus = mock(QueryBus.class);
    private final QueryHandlerAdapter queryHandlerAdapter = spy(new TestQueryHandlerAdapter());

    private QueryHandlerSubscriber testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new QueryHandlerSubscriber();
        testSubject.setApplicationContext(applicationContext);
        testSubject.setQueryBus(queryBus);
        testSubject.setQueryHandlers(Collections.singletonList(queryHandlerAdapter));
    }

    @Test
    void testStart() {
        testSubject.start();

        verify(queryHandlerAdapter).subscribe(queryBus);
        verify(applicationContext).publishEvent(isA(QueryHandlersSubscribedEvent.class));
    }

    private static class TestQueryHandlerAdapter implements QueryHandlerAdapter {

        @Override
        public Registration subscribe(QueryBus queryBus) {
            return null;
        }
    }
}