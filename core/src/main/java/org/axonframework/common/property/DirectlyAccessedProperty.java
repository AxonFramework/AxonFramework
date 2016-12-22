package org.axonframework.common.property;

import java.lang.reflect.Field;

import static java.lang.String.format;

/**
 * Property implementation that accesses public field to obtain a value of a property for a given instance.
 * @param <T> The type of object defining this property
 */
public class DirectlyAccessedProperty<T> implements Property<T> {

	private final Field field;
	private final String property;

	public DirectlyAccessedProperty(Field field, String property){
		this.field = field;
		this.property = property;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <V> V getValue(T target) {
		try {
			return (V)field.get(target);
		}
		catch (IllegalAccessException e) {
			throw new PropertyAccessException(format(
					"Failed to get value of '%s' in '%s'. Property should be accessible",
					property, target.getClass().getName()), e);
		}
	}
}
