/*
 * Copyright (c) 2010-2021. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling;

import org.axonframework.messaging.annotation.MessageHandlingMember;

import java.util.List;

/**
 * Functional interface towards resolving the occurrence of a duplicate command handler member. As such it ingests two
 * {@link MessageHandlingMember} instances and returns another one as the resolution.
 *
 * @author leechedan
 * @since 4.5
 */
@FunctionalInterface
public interface DuplicateCommandHandlingMemberResolver {

    /**
     * Chooses what to do when multi handling member is detected, returning the handling member that should be selected
     * for command handling, or otherwise throwing an exception to reject registration altogether.
     *
     * @param payloadType               The name of the Command for which the duplicates were detected
     * @param messageHandlingMemberList the {@link MessageHandlingMember} that is group of handler members handling same
     *                                  command
     * @return the resolved {@link MessageHandlingMember}. Could be one of the {@code messageHandlingMemberList} or
     *         another handling member entirely
     * @throws RuntimeException when initialize should fail
     */
    <T> MessageHandlingMember<? super T> resolve(Class<?> payloadType,
                                                 List<MessageHandlingMember<? super T>> messageHandlingMemberList);
}
