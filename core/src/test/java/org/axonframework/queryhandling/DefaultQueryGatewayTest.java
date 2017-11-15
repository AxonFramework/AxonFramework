package org.axonframework.queryhandling;

import org.axonframework.messaging.MessageDispatchInterceptor;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

public class DefaultQueryGatewayTest {

    private MessageDispatchInterceptor<QueryMessage<?, ?>> mockDispatchInterceptor;
    private QueryBus mockBus;
    private DefaultQueryGateway testSubject;

    @Before
    public void setUp() throws Exception {
        mockDispatchInterceptor = mock(MessageDispatchInterceptor.class);
        mockBus = mock(QueryBus.class);
        testSubject = new DefaultQueryGateway(mockBus, mockDispatchInterceptor);
        when(mockDispatchInterceptor.handle(isA(QueryMessage.class))).thenAnswer(i -> i.getArguments()[0]);
    }

    @Test
    public void testDispatchSingleResultQuery() throws Exception {
        when(mockBus.query(any())).thenReturn(CompletableFuture.completedFuture("answer"));

        CompletableFuture<String> actual = testSubject.send("query", String.class);
        assertEquals("answer", actual.get());

        verify(mockBus).query(argThat(new TypeSafeMatcher<QueryMessage<String, String>>() {
            @Override
            protected boolean matchesSafely(QueryMessage<String, String> item) {
                return "query".equals(item.getPayload());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a QueryMessage containing the 'query' payload");
            }
        }));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testDispatchMultiResultQuery() throws Exception {
        when(mockBus.queryAll(any(), anyLong(), any())).thenReturn(Stream.of("answer"));

        Stream<String> actual = testSubject.send("query", String.class, 1, TimeUnit.SECONDS);
        assertEquals("answer", actual.findFirst().get());

        verify(mockBus).queryAll(argThat(new TypeSafeMatcher<QueryMessage<String, String>>() {
            @Override
            protected boolean matchesSafely(QueryMessage<String, String> item) {
                return "query".equals(item.getPayload());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a QueryMessage containing the 'query' payload");
            }
        }), eq(1L), eq(TimeUnit.SECONDS));
    }
}
