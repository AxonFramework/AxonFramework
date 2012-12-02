package org.axonframework.common;


/**
 * Relies on Uniform Access Principle (see on <a href="http://en.wikipedia.org/wiki/Uniform_access_principle">wikipedia</a>)
 * to retrieve value of property.
 * For property {@code myProperty} will try to use method {@code myProperty()}
 */
public class UniformStylePropertyAccessor
        extends MethodPropertyAccessor {

    public UniformStylePropertyAccessor(String aProperty) {
        super(aProperty);
    }

    @Override
    public String getter() {
        return property;
    }

    @Override
    public String setter() {
        return String.format("%s_$eq", property);
    }
}
