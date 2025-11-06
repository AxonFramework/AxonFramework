/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.update.detection;

import org.axonframework.common.annotation.Internal;

/**
 * Utility class to retrieve the current Kotlin version at runtime. It attempts to access the `kotlin.KotlinVersion`
 * class and invoke its `getCurrent` method. If the class or method is not found, it returns "none".
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Internal
public class KotlinVersion {

    private static String KOTLIN_VERSION = null;

    /**
     * Returns the current Kotlin version as a string. If the Kotlin library is not present, it returns "none".
     *
     * @return the current Kotlin version or "none" if not available
     */
    public static String get() {
        if (KOTLIN_VERSION == null || KOTLIN_VERSION.isEmpty()) {
            KOTLIN_VERSION = detect();
        }
        return KOTLIN_VERSION;
    }

    private static String detect() {
        try {
            Class<?> versionClass = Class.forName("kotlin.KotlinVersion");
            return versionClass.getDeclaredMethod("getCurrent").invoke(null).toString();
        } catch (Exception e) {
            return "none";
        }
    }
}
