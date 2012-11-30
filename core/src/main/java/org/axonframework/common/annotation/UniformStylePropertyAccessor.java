package org.axonframework.common.annotation;

/**
 * Relies on Uniform Access Principle (see on <a href="http://en.wikipedia.org/wiki/Uniform_access_principle">wikipedia</a>)
 * to retrieve value of property.
 * For property {@code myProperty} will try to use method {@code myProperty()}
 */
public class UniformStylePropertyAccessor
        extends AbstractPropertyAccessor {

    @Override
    public String methodName(String propertyName) {
        return propertyName;
    }
}
