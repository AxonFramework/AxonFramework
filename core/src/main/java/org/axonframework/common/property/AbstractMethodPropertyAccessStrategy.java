package org.axonframework.common.property;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Abstract implementation of the PropertyAccessStrategy that uses a no-arg, public method to access the property
 * value. The name of the method can be derived from the name of the property.
 *
 * @author Maxim Fedorov
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractMethodPropertyAccessStrategy extends PropertyAccessStrategy {

    private static final Logger logger = LoggerFactory.getLogger(BeanPropertyAccessStrategy.class);

    @Override
    public <T> Property<T> propertyFor(Class<T> targetClass, String property) {
        String methodName = getterName(property);
        try {
            final Method method = targetClass.getMethod(methodName);
            if (!Void.TYPE.equals(method.getReturnType())) {
                return new MethodAccessedProperty<T>(method, property);
            }
            logger.debug(
                    "Method with name '{}' in '{}' cannot be accepted as a property accessor, as it returns void",
                    methodName, targetClass.getName());
        } catch (NoSuchMethodException e) {
            logger.debug("No method with name '{}' found in {}", new Object[]{methodName, targetClass.getName(), e});
        }
        return null;
    }

    /**
     * Returns the name of the method that is used to access the property.
     *
     * @param property The property to access
     * @return the name of the method use as accessor
     */
    protected abstract String getterName(String property);
}
