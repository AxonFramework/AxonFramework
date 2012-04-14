/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common;

import java.lang.reflect.Field;
import java.security.PrivilegedAction;

/**
 * PrivilegedAction that makes the given field accessible for reflection.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class FieldAccessibilityCallback implements PrivilegedAction<Object> {

    private final Field field;

    /**
     * Initialize the callback to make the given <code>field</code> accessible for reflection.
     *
     * @param field The field to make accessible
     */
    public FieldAccessibilityCallback(Field field) {
        this.field = field;
    }

    @Override
    public Object run() {
        field.setAccessible(true);
        return null;
    }
}
