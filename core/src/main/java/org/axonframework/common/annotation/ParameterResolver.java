package org.axonframework.common.annotation;

import org.axonframework.domain.Message;

/**
 * Interface for a mechanism that resolves handler method parameter values from a given {@link Message}.
 *
 * @param <T> The type of parameter returned by this resolver
 * @author Allard Buijze
 * @since 2.0
 */
public interface ParameterResolver<T> {

    /**
     * Resolves the parameter value to use for the given <code>message</code>, or <code>null</code> if no suitable
     * parameter value can be resolved.
     *
     * @param message The message to resolve the value from
     * @return the parameter value for the handler
     */
    T resolveParameterValue(Message message);

    /**
     * Indicates whether this resolver is capable of providing a value for the given <code>message</code>.
     *
     * @param message The message to evaluate
     * @return <code>true</code> if this resolver can provide a value for the message, otherwise <code>false</code>
     */
    boolean matches(Message message);
}
