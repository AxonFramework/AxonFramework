package org.axonframework.spring.config;

import org.axonframework.config.Configuration;
import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.config.ModuleConfiguration;
import org.junit.*;
import org.mockito.*;
import org.springframework.core.annotation.Order;

import java.util.Arrays;
import java.util.function.Function;

import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.*;

public class EventHandlerRegistrarTest {

    private AxonConfiguration axonConfig;
    private ModuleConfiguration eventConfiguration;
    private EventProcessingConfigurer eventConfigurer;
    private EventHandlerRegistrar testSubject;

    @Before
    public void setUp() {
        axonConfig = mock(AxonConfiguration.class);
        eventConfiguration = mock(ModuleConfiguration.class);
        eventConfigurer = mock(EventProcessingConfigurer.class);
        testSubject = new EventHandlerRegistrar(axonConfig, eventConfiguration, eventConfigurer);
    }

    @Test
    public void testBeansRegisteredInOrder() {
        testSubject.setEventHandlers(Arrays.asList(new OrderedBean(), new LateOrderedBean(), new UnorderedBean()));

        InOrder inOrder = Mockito.inOrder(eventConfigurer);
        inOrder.verify(eventConfigurer).registerEventHandler(returns(OrderedBean.class));
        inOrder.verify(eventConfigurer).registerEventHandler(returns(LateOrderedBean.class));
        inOrder.verify(eventConfigurer).registerEventHandler(returns(UnorderedBean.class));
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
