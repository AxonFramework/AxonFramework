/*
 * Copyright (c) 2010-2023. Axon Framework
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.axonserver.connector;

import org.axonframework.messaging.Message;

/**
 * Simple TargetContextResolver implementation  to be able to spy an instance of it for testing.
 *
 * @author Steven van Beelen
 */
public class TestTargetContextResolver<T extends Message<?>> implements TargetContextResolver<T> {

    public static final String BOUNDED_CONTEXT = "not-important";

    @Override
    public String resolveContext(T message) {
        return BOUNDED_CONTEXT;
    }
}
