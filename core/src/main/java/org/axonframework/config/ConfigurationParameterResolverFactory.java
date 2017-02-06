package org.axonframework.config;

import org.axonframework.common.Priority;
import org.axonframework.messaging.annotation.FixedValueParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

import static org.axonframework.common.Priority.LOW;

/**
 * ParameterResolverFactory implementation that resolves parameters from available components in the Configuration
 * instance it was configured with.
 * <p>
 * This implementation is usually auto-configured when using the Configuration API.
 */
@Priority(LOW)
public class ConfigurationParameterResolverFactory implements ParameterResolverFactory {

    private final Configuration configuration;

    /**
     * Initialize an instance using given {@code configuration} to supply the value to resolve parameters with
     *
     * @param configuration The configuration to look for component
     */
    public ConfigurationParameterResolverFactory(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public ParameterResolver<?> createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
        Object component = configuration.getComponent(parameters[parameterIndex].getType());
        return component == null ? null : new FixedValueParameterResolver<>(component);
    }
}
