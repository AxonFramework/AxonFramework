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

package org.axonframework.test.saga;

import org.hamcrest.Description;

import java.util.List;

/**
 * Utility class for creating a description.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public abstract class DescriptionUtils {

    private DescriptionUtils() {
    }

    /**
     * Describe the contents of the given {@code list} in the given {@code description}.
     *
     * @param list        The list to describe
     * @param description The description to describe to
     */
    public static void describe(List<?> list, Description description) {
        int counter = 0;
        description.appendText("List with ");
        for (Object item : list) {
            description.appendText("<")
                       .appendText(item != null ? item.toString() : "null")
                       .appendText(">");
            if (counter == list.size() - 2) {
                description.appendText(" and ");
            } else if (counter < list.size() - 2) {
                description.appendText(", ");
            }
            counter++;
        }
    }
}
