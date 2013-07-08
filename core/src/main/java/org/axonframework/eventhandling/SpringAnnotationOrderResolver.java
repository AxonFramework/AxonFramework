package org.axonframework.eventhandling;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

/**
 * OrderResolver implementation that checks for {@link Order @Order} annotations on the type level. When such
 * annotation is found, the value provided is used as order.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public class SpringAnnotationOrderResolver implements OrderResolver {

    @Override
    public int orderOf(EventListener listener) {
        Order order = AnnotationUtils.findAnnotation(listener.getClass(), Order.class);
        if (order == null && listener instanceof EventListenerProxy) {
            order = AnnotationUtils.findAnnotation(((EventListenerProxy) listener).getTargetType(), Order.class);
        }
        return order == null ? 0 : order.value();
    }
}
