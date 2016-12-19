package org.axonframework.spring.config;

import org.axonframework.config.Configuration;
import org.axonframework.config.EventHandlingConfiguration;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.core.annotation.Order;

import java.util.Arrays;
import java.util.function.Function;

import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;

public class EventHandlerRegistrarTest {

    private AxonConfiguration axonConfig;
    private EventHandlingConfiguration eventConfig;
    private EventHandlerRegistrar testSubject;

    @Before
    public void setUp() throws Exception {
        axonConfig = mock(AxonConfiguration.class);
        eventConfig = mock(EventHandlingConfiguration.class);
        testSubject = new EventHandlerRegistrar(axonConfig, eventConfig);
    }

    @Test
    public void testBeansRegisteredInOrder() throws Exception {
        testSubject.setEventHandlers(Arrays.asList(new OrderedBean(), new LateOrderedBean(), new UnorderedBean()));

        InOrder inOrder = Mockito.inOrder(eventConfig);
        inOrder.verify(eventConfig).registerEventHandler(returns(OrderedBean.class));
        inOrder.verify(eventConfig).registerEventHandler(returns(LateOrderedBean.class));
        inOrder.verify(eventConfig).registerEventHandler(returns(UnorderedBean.class));
    }

    private Function<Configuration, Object> returns(Class<?> type) {
        return argThat(new TypeSafeMatcher<Function<Configuration,Object>>() {
            @Override
            protected boolean matchesSafely(Function<Configuration, Object> item) {
                Object actual = item.apply(axonConfig);
                return type.isInstance(actual);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a function returning a ")
                        .appendValue(type.getSimpleName());
            }
        });
    }

    public static class UnorderedBean {

    }

    @Order(0)
    public static class OrderedBean {

    }

    @Order(100)
    public static class LateOrderedBean {

    }

}
