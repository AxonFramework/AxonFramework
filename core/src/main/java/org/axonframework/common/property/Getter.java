package org.axonframework.common.property;


public interface Getter {
    <V> V getValue(Object target);
}
