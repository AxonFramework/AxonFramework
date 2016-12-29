package org.axonframework.spring.messaging;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.assertEquals;

@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class ApplicationContextEventPublisherTest {

    @Autowired
    private ListenerBean listenerBean;

    @Autowired
    private EventBus eventBus;

    @Test
    public void testEventsForwardedToListenerBean() throws Exception {
        eventBus.publish(asEventMessage("test"));

        assertEquals("test", listenerBean.getEvents().get(0));
    }

    @Configuration
    public static class Context {

        @Bean
        public ListenerBean listenerBean() {
            return new ListenerBean();
        }

        @Bean
        public EventBus eventBus() {
            return new SimpleEventBus();
        }

        @Bean
        public ApplicationContextEventPublisher publisher(EventBus eventBus) {
            return new ApplicationContextEventPublisher(eventBus);
        }

    }
    public static class ListenerBean {

        private List<Object> events = new ArrayList<>();

        @EventListener
        public void handle(PayloadApplicationEvent<String> event) {
            events.add(event.getPayload());
        }

        public List<Object> getEvents() {
            return events;
        }
    }
}
