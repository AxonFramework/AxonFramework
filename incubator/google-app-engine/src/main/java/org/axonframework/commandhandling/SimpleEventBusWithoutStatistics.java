package org.axonframework.commandhandling;

import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.EventListenerProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * <p>This is a temporary copy of the SimpleEventBus, made to run on google app engine. We removed all the statistics
 * stuff. We are still looking for a better solution.</p>
 *
 * @author Jettro Coenradie
 */
public class SimpleEventBusWithoutStatistics implements EventBus {

    private static final Logger logger = LoggerFactory.getLogger(SimpleEventBusWithoutStatistics.class);
    private final Set<EventListener> listeners = new CopyOnWriteArraySet<EventListener>();

    /**
     * Initializes the SimpleEventBus.
     */
    public SimpleEventBusWithoutStatistics() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unsubscribe(EventListener eventListener) {
        if (listeners.remove(eventListener)) {
            Object listener = getActualListenerFrom(eventListener);
            logger.debug("EventListener {} unsubscribed successfully", eventListener.getClass().getSimpleName());
        } else {
            logger.info("EventListener {} not removed. It was already unsubscribed",
                    eventListener.getClass().getSimpleName());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void subscribe(EventListener eventListener) {
        if (listeners.add(eventListener)) {
            Object listener = getActualListenerFrom(eventListener);
            logger.debug("EventListener [{}] subscribed successfully", eventListener.getClass().getSimpleName());
        } else {
            logger.info("EventListener [{}] not added. It was already subscribed",
                    eventListener.getClass().getSimpleName());
        }
    }

    private Object getActualListenerFrom(EventListener eventListener) {
        Object listener = eventListener;
        while (listener instanceof EventListenerProxy) {
            listener = ((EventListenerProxy) listener).getTarget();
        }
        return listener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void publish(Event event) {

        for (EventListener listener : listeners) {
            logger.debug("Dispatching Event [{}] to EventListener [{}]",
                    event.getClass().getSimpleName(),
                    listener.getClass().getSimpleName());
            listener.handle(event);
        }
    }

}
