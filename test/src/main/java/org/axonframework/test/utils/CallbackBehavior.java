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

package org.axonframework.test.utils;

import org.axonframework.messaging.MetaData;

/**
 * Interface towards a mechanism that replicates the behavior of a Command Handling component. The goal of this
 * component is to mimic behavior on the callback.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface CallbackBehavior {

    /**
     * Invoked when the Command Bus receives a Command that is dispatched with a Callback method. The return value of
     * this invocation is used to invoke the callback.
     *
     * @param commandPayload  The payload of the Command Message
     * @param commandMetaData The MetaData of the CommandMessage
     * @return any return value to pass to the callback's onResult method.
     *
     * @throws Exception If the onFailure method of the callback must be invoked
     */
    Object handle(Object commandPayload, MetaData commandMetaData) throws Exception;
}
