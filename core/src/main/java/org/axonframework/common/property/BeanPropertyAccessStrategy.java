package org.axonframework.common.property;

import static java.lang.String.format;
import static java.util.Locale.ENGLISH;

/**
 * BeanPropertyAccessStrategy implementation that uses JavaBean style property access. This means that for any given
 * property 'property', a method "getProperty" is expected to provide the property value
 *
 * @author Maxim Fedorov
 * @author Allard Buijze
 * @since 2.0
 */
public class BeanPropertyAccessStrategy extends AbstractMethodPropertyAccessStrategy {

    @Override
    protected String getterName(String property) {
        return format(ENGLISH, "get%S%s", property.charAt(0), property.substring(1));
    }

    @Override
    protected int getPriority() {
        return 0;
    }
}
