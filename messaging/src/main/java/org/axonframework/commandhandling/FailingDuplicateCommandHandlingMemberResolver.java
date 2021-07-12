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
 * Implementation of the {@link DuplicateCommandHandlingMemberResolver} that throws a {@link
 * DuplicateCommandHandlingMemberException} when multi handler members are detected.
 *
 * @author leechedan
 * @since 4.5
 */
public class FailingDuplicateCommandHandlingMemberResolver implements DuplicateCommandHandlingMemberResolver {

    private static final FailingDuplicateCommandHandlingMemberResolver INSTANCE =
            new FailingDuplicateCommandHandlingMemberResolver();

    /**
     * @return a DuplicateCommandHandlingMemberResolver that throws an exception when multi handler members are detected
     */
    public static FailingDuplicateCommandHandlingMemberResolver instance() {
        return INSTANCE;
    }

    private FailingDuplicateCommandHandlingMemberResolver() {
    }

    public <T> MessageHandlingMember<? super T> resolve(Class<?> payloadType,
                                                        List<MessageHandlingMember<? super T>> messageHandlingMemberList) {

        throw new DuplicateCommandHandlingMemberException(payloadType, messageHandlingMemberList);
    }
}
