package org.axonframework.eventhandling;

import org.axonframework.domain.EventMessage;
import org.axonframework.domain.MetaData;
import org.axonframework.unitofwork.CurrentUnitOfWork;

import java.util.Collections;
import java.util.Map;

import static org.axonframework.domain.GenericEventMessage.asEventMessage;

/**
 * Template class that dispatches Events to an {@link org.axonframework.eventhandling.EventBus}, taking active
 * UnitOfWork into account. When a UnitOfWork is active, the template will ensure correct ordering with any other
 * events published during the course of that UnitOfWork. If no unit of work is active, the Event is published
 * immediately to the Event Bus.
 * <p/>
 * The template also allows addition of Meta Data properties to all Events sent through this template.
 *
 * @author Allard Buijze
 * @since 2.2
 */
public class EventTemplate {

    private final EventBus eventBus;
    private final MetaData additionalMetaData;

    /**
     * Initialize the template to publish Events through the given <code>eventBus</code>.
     *
     * @param eventBus The EventBus to publish Event Messages on.
     */
    public EventTemplate(EventBus eventBus) {
        this(eventBus, Collections.<String, Object>emptyMap());
    }

    /**
     * Initialize the template to publish Events through the given <code>eventBus</code>. The given
     * <code>additionalMetaData</code> is attached to all events published through this instance.
     *
     * @param eventBus           The EventBus to publish Event Messages on.
     * @param additionalMetaData The meta data to add to messages sent
     */
    public EventTemplate(EventBus eventBus, Map<String, ?> additionalMetaData) {
        this.eventBus = eventBus;
        this.additionalMetaData = MetaData.from(additionalMetaData);
    }

    public void publishEvent(Object payload) {
        doPublish(createEventMessage(payload));
    }

    public void publishEvent(Object payload, Map<String, ?> metaData) {
        doPublish(createEventMessage(payload).andMetaData(metaData));
    }

    private void doPublish(EventMessage<?> message) {
        if (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().publishEvent(message, eventBus);
        } else {
            eventBus.publish(message);
        }
    }

    private EventMessage<Object> createEventMessage(Object payload) {
        return asEventMessage(payload).andMetaData(additionalMetaData);
    }
}
