package org.axonframework.common.property;

import java.lang.reflect.Field;

/**
 * Implementation of PropertyAccessStrategy that scans class hierarchy to get public field named "property"
 */
public class DirectPropertyAccessStrategy extends PropertyAccessStrategy {

	@Override
	protected int getPriority() {
		return -2048;
	}

	@Override
	protected <T> Property<T> propertyFor(Class<? extends T> targetClass, String property) {
		Field[] fields = targetClass.getFields();
		for(Field field : fields) {
			if (field.getName().equals(property)) {
				return new DirectlyAccessedProperty<>(field, property);
			}
		}
		return null;
	}
}
