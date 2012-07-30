package org.axonframework.common.annotation;

import org.axonframework.domain.Message;

/**
 * ParameterResolver implementation that injects a fixed value. Useful for injecting parameter values that do not rely
 * on information contained in the incoming message itself.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class FixedValueParameterResolver<T> implements ParameterResolver<T> {

    private final T value;

    /**
     * Initialize the ParameterResolver to inject the given <code>value</code> for each incoming message.
     *
     * @param value The value to inject as parameter
     */
    public FixedValueParameterResolver(T value) {
        this.value = value;
    }

    @Override
    public T resolveParameterValue(Message message) {
        return value;
    }

    @Override
    public boolean matches(Message message) {
        return true;
    }
}
