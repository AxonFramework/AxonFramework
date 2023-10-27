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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.Message;

import java.util.Map;

public class ThrowingCorrelationDataProvider implements CorrelationDataProvider{
    @Override
    public Map<String, ?> correlationDataFor(Message<?> message) {
        throw new AxonConfigurationException("correlation is not clear");
    }
}
