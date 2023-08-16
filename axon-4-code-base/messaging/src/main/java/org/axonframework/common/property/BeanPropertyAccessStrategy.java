/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
