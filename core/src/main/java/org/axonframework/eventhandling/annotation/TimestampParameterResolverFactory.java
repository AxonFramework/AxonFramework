package org.axonframework.eventhandling.annotation;

import org.axonframework.common.annotation.ParameterResolver;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.Message;
import org.joda.time.DateTime;

import java.lang.annotation.Annotation;

import static org.axonframework.common.CollectionUtils.getAnnotation;

/**
 * ParameterResolverFactory implementation that accepts parameters of a {@link DateTime} type that have been annotated
 * with the {@link Timestamp} annotation and assigns the timestamp of the EventMessage.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class TimestampParameterResolverFactory extends ParameterResolverFactory implements ParameterResolver<DateTime> {

    @Override
    protected ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                               Annotation[] parameterAnnotations) {
        Timestamp annotation = getAnnotation(parameterAnnotations, Timestamp.class);
        if (parameterType.isAssignableFrom(DateTime.class) && annotation != null) {
            return this;
        }
        return null;
    }

    @Override
    public DateTime resolveParameterValue(Message message) {
        if (message instanceof EventMessage) {
            return ((EventMessage) message).getTimestamp();
        }
        return null;
    }

    @Override
    public boolean matches(Message message) {
        return message instanceof EventMessage;
    }

    @Override
    public boolean supportsPayloadResolution() {
        return false;
    }
}
