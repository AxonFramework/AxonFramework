package org.axonframework.test.matchers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * FieldFilter implementation that only accepts non-static Fields.
 *
 * @author bliessens
 * @since 3.3
 */
public class NonStaticFieldsFilter implements FieldFilter {

    private static final NonStaticFieldsFilter INSTANCE = new NonStaticFieldsFilter();

    public static NonStaticFieldsFilter instance() {
        return INSTANCE;
    }

    private NonStaticFieldsFilter() {
    }

    @Override
    public boolean accept(Field field) {
        return !Modifier.isStatic(field.getModifiers());
    }

}
