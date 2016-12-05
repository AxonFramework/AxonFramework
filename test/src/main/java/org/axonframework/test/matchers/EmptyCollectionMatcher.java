/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test.matchers;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

import java.util.Collection;
import java.util.List;

/**
 * Matches any empty collection.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class EmptyCollectionMatcher extends BaseMatcher<List<?>> {

    private final String contentDescription;

    /**
     * Creates a matcher of a list of empty items. The name of the item type (in plural) is passed in the given
     * {@code contentDescription} and will be part of the description of this matcher.
     *
     * @param contentDescription The description of the content type of the collection
     */
    public EmptyCollectionMatcher(String contentDescription) {
        this.contentDescription = contentDescription;
    }

    @Override
    public boolean matches(Object item) {
        return item instanceof Collection && ((Collection) item).isEmpty();
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("no ");
        description.appendText(contentDescription);
    }
}
