/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.test.matchers;

import java.lang.reflect.Field;

/**
 * FieldFilter implementation that accepts all Fields.
 *
 * @author Allard Buijze
 * @since 2.4.1
 */
public class AllFieldsFilter implements FieldFilter {

    private static final AllFieldsFilter INSTANCE = new AllFieldsFilter();

    @Override
    public boolean accept(Field field) {
        return true;
    }

    private AllFieldsFilter() {
    }

    /**
     * Returns the (singleton) instance of the AllFieldsFilter
     *
     * @return an AllFieldsFilter instance
     */
    public static AllFieldsFilter instance() {
        return INSTANCE;
    }
}
