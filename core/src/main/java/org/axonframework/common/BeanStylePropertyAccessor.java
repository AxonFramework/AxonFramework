package org.axonframework.common;

import static java.lang.String.format;
import static java.util.Locale.ENGLISH;

/**
 * Relies on JavaBean notation to retrieve method.
 * For property {@code myProperty} will try to use method {@code getMyProperty()}
 */
public class BeanStylePropertyAccessor
        extends MethodPropertyAccessor {

    public BeanStylePropertyAccessor(String aProperty) {
        super(aProperty);
    }

    public String getter() {
        return "get" + capitalizedProperty();
    }

    @Override
    public String setter() {
        return "set" + capitalizedProperty();
    }

    private String capitalizedProperty() {
        return format(ENGLISH, "%S%s", property.charAt(0), property.substring(1));
    }
}
