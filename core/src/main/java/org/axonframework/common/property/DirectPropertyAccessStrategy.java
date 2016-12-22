package org.axonframework.common.property;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.axonframework.common.ReflectionUtils.fieldsOf;

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
		Iterable<Field> fields = fieldsOf(targetClass);
		for(Field field : fields) {
			if (field.getName().equals(property) && (field.getModifiers() & Modifier.PUBLIC) == Modifier.PUBLIC) {
				return new DirectlyAccessedProperty<>(field, property);
			}
		}
		return null;
	}
}
