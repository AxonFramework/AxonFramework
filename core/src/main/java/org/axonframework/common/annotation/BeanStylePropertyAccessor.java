package org.axonframework.common.annotation;

import static java.lang.String.format;
import static java.util.Locale.ENGLISH;

/**
 * Relies on JavaBean notation to retrieve method.
 * For property {@code myProperty} will try to use method {@code getMyProperty()}
 */
public class BeanStylePropertyAccessor
        extends AbstractPropertyAccessor {

    public String methodName(String propertyName) {
        return format(ENGLISH,"get%S%s", propertyName.charAt(0), propertyName.substring(1));
    }

}
