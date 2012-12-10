package org.axonframework.common;


/**
 * Extend {@link AbstractPropertyAccessor} or {@code MethodPropertyAccessor}
 * instead of implementing this.
 */
public interface PropertyAccessor {
    <T, V> V getValue(T target);
    <T, V> void setValue(V value, T target);
}
