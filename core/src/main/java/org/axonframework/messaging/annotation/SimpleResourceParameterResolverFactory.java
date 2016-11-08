package org.axonframework.messaging.annotation;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

import org.axonframework.common.Priority;

/**
 * A {@link ParameterResolverFactory} implementation for simple resource injections.
 * Uses the {@link FixedValueParameterResolver} to inject a resource as a fixed value
 * on message handling if the resource equals a message handling method parameter.
 */
@Priority(Priority.LOW)
public class SimpleResourceParameterResolverFactory implements ParameterResolverFactory {

    private final Object resource;

    /**
     * Initialize the ParameterResolverFactory to inject the given
     * <code>resource</code> in applicable parameters.
     *
     * @param resource The resource to inject
     */
    public SimpleResourceParameterResolverFactory(Object resource) {
        this.resource = resource;
    }

    @Override
    public ParameterResolver createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
        if (parameters[parameterIndex].getType().isInstance(resource)) {
            return new FixedValueParameterResolver<>(resource);
        }
        return null;
    }

}
