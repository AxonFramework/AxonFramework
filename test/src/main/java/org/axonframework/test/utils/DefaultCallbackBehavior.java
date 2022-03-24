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

import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.messaging.MetaData;

/**
 * Default implementation of the CallbackBehavior interface. This implementation always returns {@code null}, which
 * results in the {@link org.axonframework.commandhandling.CommandCallback#onResult(org.axonframework.commandhandling.CommandMessage,
 * CommandResultMessage)} method to be invoked with a {@code null} result
 * parameter.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DefaultCallbackBehavior implements CallbackBehavior {

    @Override
    public Object handle(Object commandPayload, MetaData commandMetaData) {
        return null;
    }
}
