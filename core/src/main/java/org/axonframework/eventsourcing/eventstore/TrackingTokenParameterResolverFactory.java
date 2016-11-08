package org.axonframework.eventsourcing.eventstore;


import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

/**
 * Implementation of a {@link ParameterResolverFactory} that resolves the {@link TrackingToken} of an event message
 * if that message is a {@link TrackedEventMessage}.
 */
public class TrackingTokenParameterResolverFactory implements ParameterResolverFactory {

    private static final TrackingTokenParameterResolver RESOLVER = new TrackingTokenParameterResolver();

    @Override
    public ParameterResolver createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
        if (TrackingToken.class.equals(parameters[parameterIndex].getType())) {
            return RESOLVER;
        }
        return null;
    }

    private static class TrackingTokenParameterResolver implements ParameterResolver<TrackingToken> {

        @Override
        public TrackingToken resolveParameterValue(Message<?> message) {
            return ((TrackedEventMessage) message).trackingToken();
        }

        @Override
        public boolean matches(Message<?> message) {
            return message instanceof TrackedEventMessage;
        }
    }
}
