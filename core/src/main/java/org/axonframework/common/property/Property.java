package org.axonframework.common.property;

/**
 * Interface describing a mechanism that can read a predefined property from a given instance.
 *
 * @param <T> The type of object defining this property
 * @author Maxim Fedorov
 * @author Allard Buijze
 * @since 2.0
 */
public interface Property<T> {

    /**
     * Returns the value of the property on given <code>target</code>.
     *
     * @param target The instance to get the property value from
     * @param <V>    The type of value expected
     * @return the property value on <code>target</code>
     */
    <V> V getValue(T target);
}
