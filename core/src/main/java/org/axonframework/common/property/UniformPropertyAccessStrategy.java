package org.axonframework.common.property;


/**
 * Relies on Uniform Access Principle
 * (see on <a href="http://en.wikipedia.org/wiki/Uniform_access_principle">wikipedia</a>).
 * For property {@code myProperty} will use method {@code myProperty()}
 */
public class UniformPropertyAccessStrategy extends MethodPropertyAccessStrategy {
    @Override
    protected String getterName(String property) {
        return property;
    }
}
