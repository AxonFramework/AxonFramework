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

package org.axonframework.eventhandling.replay;

import org.axonframework.messaging.annotation.MessageHandlingMember;

/**
 * Interface describing a message handler capable of handling {@link ResetMessage}s.
 *
 * @param <T> the entity type to which the message handler will delegate the actual handling of the message
 * @author Steven van Beelen
 * @since 4.4
 */
public interface ResetHandlingMember<T> extends MessageHandlingMember<T> {

}
