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

package org.axonframework.messaging.correlation;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * CorrelationDataProvider implementation defines correlation headers by the header names. The headers from messages
 * with these keys are returned as correlation data.
 *
 * @author Allard Buijze
 * @since 2.3
 */
public class SimpleCorrelationDataProvider implements CorrelationDataProvider {

    private final String[] headerNames;

    /**
     * Initializes the CorrelationDataProvider to return the meta data of messages with given {@code metaDataKeys}
     * as correlation data.
     *
     * @param metaDataKeys The keys of the meta data entries from messages to return as correlation data
     */
    public SimpleCorrelationDataProvider(String... metaDataKeys) {
        this.headerNames = Arrays.copyOf(metaDataKeys, metaDataKeys.length);
    }

    @Override
    public Map<String, ?> correlationDataFor(Message<?> message) {
        if (headerNames.length == 0) {
            return Collections.emptyMap();
        }
        Map<String, Object> data = new HashMap<>();
        final MetaData metaData = message.getMetaData();
        for (String headerName : headerNames) {
            if (metaData.containsKey(headerName)) {
                data.put(headerName, metaData.get(headerName));
            }
        }
        return data;
    }
}
