/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.insight;

/**
 * Used to check for Axon 1.x or higher version being used.
 *
 * @author Joris Kuipers
 * @since 2.0
 */
public class AxonVersion {

    static boolean IS_AXON_1X;

    static {
        try {
            Class.forName("org.axonframework.domain.Event");
            IS_AXON_1X = true;
        } catch (ClassNotFoundException e) {
            IS_AXON_1X = false;
        }
    }
}
