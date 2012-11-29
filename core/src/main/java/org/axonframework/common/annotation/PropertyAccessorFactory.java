package org.axonframework.common.annotation;

import org.axonframework.saga.annotation.SagaEventHandler;

public class PropertyAccessorFactory {
    static public PropertyAccessor instanceFor(SagaEventHandler handler) {
        Class<? extends PropertyAccessor> accessor = handler.accessor();

        if (accessor.isAssignableFrom(BeanStylePropertyAccessor.class))
            return new BeanStylePropertyAccessor();
        if (accessor.isAssignableFrom(UnifiedStylePropertyAccessor.class))
            return new UnifiedStylePropertyAccessor();

        return new BeanStylePropertyAccessor();
    }

}
