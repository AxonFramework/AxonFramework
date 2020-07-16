/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.axonserver.connector.utils;

import com.thoughtworks.xstream.XStream;
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
     * Return a {@link XStreamSerializer} for which the security settings have been set.
     *
     * @return a {@link XStreamSerializer} for which the security settings have been set.
     */
    public static XStreamSerializer secureXStreamSerializer() {
        XStream xStream = new XStream();
        xStream.setClassLoader(TestSerializer.class.getClassLoader());
        xStream.allowTypesByWildcard(new String[]{"org.axonframework.**"});
        return XStreamSerializer.builder().xStream(xStream).build();
    }
}
