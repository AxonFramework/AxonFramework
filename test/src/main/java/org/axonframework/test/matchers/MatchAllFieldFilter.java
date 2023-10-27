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

package org.axonframework.test.matchers;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * FieldFilter that delegates to an arbitrary number of other filters. This filter accepts any field that is accepted
 * by all registered filters.
 * <p/>
 * By default, all fields are accepted.
 *
 * @author Allard Buijze
 * @since 2.4.1
 */
public class MatchAllFieldFilter implements FieldFilter {

    private final List<FieldFilter> filters = new ArrayList<>();

    /**
     * Initializes a filter that accepts any field that is accepted by all given {@code filters}
     *
     * @param filters The filters to use to evaluate a given Field
     */
    public MatchAllFieldFilter(Collection<FieldFilter> filters) {
        this.filters.addAll(filters);
    }

    @Override
    public boolean accept(Field field) {
        for (FieldFilter filter : filters) {
            if (!filter.accept(field)) {
                return false;
            }
        }
        return true;
    }
}
