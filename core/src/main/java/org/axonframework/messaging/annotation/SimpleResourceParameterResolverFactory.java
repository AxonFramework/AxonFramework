package org.axonframework.messaging.annotation;

import org.axonframework.common.Priority;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

/**
 * A {@link ParameterResolverFactory} implementation for simple resource injections.
 * Uses the {@link FixedValueParameterResolver} to inject a resource as a fixed value
 * on message handling if the resource equals a message handling method parameter.
 */
@Priority(Priority.LOW)
public class SimpleResourceParameterResolverFactory implements ParameterResolverFactory {

    private final Iterable<?> resources;

    /**
     * Initialize the ParameterResolverFactory to inject the given {@code resource} in applicable parameters.
     *
     * @param resources The resource to inject
     */
    public SimpleResourceParameterResolverFactory(Iterable<?> resources) {
        this.resources = resources;
    }

    @Override
    public ParameterResolver createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
        for (Object resource : resources) {
            if (parameters[parameterIndex].getType().isInstance(resource)) {
                return new FixedValueParameterResolver<>(resource);
            }
        }
        return null;
    }

}
