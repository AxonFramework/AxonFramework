package org.axonframework.axonserver.connector;

import org.axonframework.messaging.Message;

/**
 * Simple TargetContextResolver implementation  to be able to spy an instance of it for testing.
 *
 * @author Steven van Beelen
 */
public class TestTargetContextResolver<T extends Message<?>> implements TargetContextResolver<T> {

    public static final String BOUNDED_CONTEXT = "not-important";

    @Override
    public String resolveContext(T message) {
        return BOUNDED_CONTEXT;
    }
}
