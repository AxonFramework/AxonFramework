package org.axonframework.common.annotation;

import static java.lang.String.format;
import static java.util.Locale.ENGLISH;

public class BeanStylePropertyAccessor
        extends AbstractPropertyAccessor {

    public String methodName(String propertyName) {
        return format(ENGLISH,"get%S%s",
                propertyName.charAt(0), propertyName.substring(1));
    }

}
