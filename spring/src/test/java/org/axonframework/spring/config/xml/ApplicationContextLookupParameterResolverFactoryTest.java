package org.axonframework.spring.config.xml;

import org.axonframework.common.annotation.FixedValueParameterResolver;
import org.axonframework.common.annotation.ParameterResolver;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.lang.annotation.Annotation;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.axonframework.domain.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/contexts/custom-parameter-resolver-context.xml")
public class ApplicationContextLookupParameterResolverFactoryTest {

    @Autowired
    private EventBus eventBus;

    @Autowired
    private CustomParameterEventListener listener;

    @Test
    public void testPublishEvent() throws Exception {
        eventBus.publish(asEventMessage(new CustomEvent()));

        assertEquals(1, listener.invocationCount());
    }

    public static class CustomParameterEventListener {

        private final AtomicInteger invocationCount = new AtomicInteger();

        @EventHandler
        public void handleEvent(CustomEvent event, TimeUnit timeUnit) {
            assertNotNull(event);
            assertNotNull(timeUnit);
            invocationCount.incrementAndGet();
        }

        public int invocationCount() {
            return invocationCount.get();
        }
    }

    public static class CustomEvent {

    }

    public static class CustomParameterResolverFactory implements ParameterResolverFactory {

        @Override
        public ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                                Annotation[] parameterAnnotations) {
            if (TimeUnit.class.isAssignableFrom(parameterType)) {
                return new FixedValueParameterResolver<>(TimeUnit.DAYS);
            }
            return null;
        }
    }
}
