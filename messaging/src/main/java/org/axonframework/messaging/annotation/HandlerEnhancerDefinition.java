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

package org.axonframework.messaging.annotation;

import javax.annotation.Nonnull;

/**
 * Interface describing objects that are capable of enhancing a {@link MessageHandler}, giving it additional
 * functionality.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public interface HandlerEnhancerDefinition {

    /**
     * Enhance the given {@code original} handler. Implementations may return the original message handler.
     *
     * @param <T>      The type of object that will perform the actual handling of the message
     * @param original The original message handler
     * @return The enhanced message handler
     */
    <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original);
}
