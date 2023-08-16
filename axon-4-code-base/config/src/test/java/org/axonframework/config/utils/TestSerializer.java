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

package org.axonframework.config.utils;

import com.thoughtworks.xstream.XStream;
import org.axonframework.serialization.xml.CompactDriver;
import org.axonframework.serialization.xml.XStreamSerializer;

/**
 * Utility providing {@link org.axonframework.serialization.Serializer} instances for testing.
 *
 * @author Steven van Beelen
 */
public abstract class TestSerializer {

    private TestSerializer() {
        // Test utility class
    }

    /**
     * Return a {@link XStreamSerializer} using a default {@link XStream} instance with a {@link CompactDriver}.
     *
     * @return a {@link XStreamSerializer} using a default {@link XStream} instance with a {@link CompactDriver}
     */
    public static XStreamSerializer xStreamSerializer() {
        return XStreamSerializer.builder()
                                .xStream(new XStream(new CompactDriver()))
                                .build();
    }
}
