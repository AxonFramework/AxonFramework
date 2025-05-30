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

package org.axonframework.messaging.correlation;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code CorrelationDataProvider} implementation defines correlation headers by the header names. The headers from
 * messages with these keys are returned as correlation data.
 *
 * @author Allard Buijze
 * @since 2.3.0
 */
public class SimpleCorrelationDataProvider implements CorrelationDataProvider {

    private final String[] headerNames;

    /**
     * Initializes the CorrelationDataProvider to return the metadata of messages with given {@code metaDataKeys} as
     * correlation data.
     *
     * @param metaDataKeys The keys of the metadata entries from messages to return as correlation data.
     */
    public SimpleCorrelationDataProvider(String... metaDataKeys) {
        this.headerNames = Arrays.copyOf(metaDataKeys, metaDataKeys.length);
    }

    @Nonnull
    @Override
    public Map<String, String> correlationDataFor(@Nonnull Message<?> message) {
        if (headerNames.length == 0) {
            return Collections.emptyMap();
        }
        Map<String, String> data = new HashMap<>();
        final MetaData metaData = message.getMetaData();
        for (String headerName : headerNames) {
            if (metaData.containsKey(headerName)) {
                data.put(headerName, metaData.get(headerName));
            }
        }
        return data;
    }
}
