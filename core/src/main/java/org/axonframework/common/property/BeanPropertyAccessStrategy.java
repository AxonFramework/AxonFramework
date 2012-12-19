package org.axonframework.common.property;

import static java.lang.String.format;
import static java.util.Locale.ENGLISH;


public class BeanPropertyAccessStrategy extends MethodPropertyAccessStrategy {

    protected String getterName(String property) {
        return format(ENGLISH, "get%S%s", property.charAt(0), property.substring(1));
    }

}
