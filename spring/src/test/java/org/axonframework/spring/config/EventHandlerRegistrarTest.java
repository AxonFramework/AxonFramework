package org.axonframework.spring.config;

import org.axonframework.config.Configuration;
import org.axonframework.config.EventHandlingConfiguration;
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
    public void setUp() {
        axonConfig = mock(AxonConfiguration.class);
        eventConfig = mock(EventHandlingConfiguration.class);
        testSubject = new EventHandlerRegistrar(axonConfig, eventConfig);
    }

    @Test
    public void testBeansRegisteredInOrder() {
        testSubject.setEventHandlers(Arrays.asList(new OrderedBean(), new LateOrderedBean(), new UnorderedBean()));

        InOrder inOrder = Mockito.inOrder(eventConfig);
        inOrder.verify(eventConfig).registerEventHandler(returns(OrderedBean.class));
        inOrder.verify(eventConfig).registerEventHandler(returns(LateOrderedBean.class));
        inOrder.verify(eventConfig).registerEventHandler(returns(UnorderedBean.class));
    }

    private Function<Configuration, Object> returns(Class<?> type) {
        return argThat(x -> {
            Object actual = x.apply(axonConfig);
            return type.isInstance(actual);
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
