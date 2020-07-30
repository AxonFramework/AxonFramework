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

package org.axonframework.axonserver.connector;

/**
 * Interface that encapsulates the instruction result publication functionality.
 *
 * @author Sara Pellegrini
 * @since 4.4
 * @deprecated in through use of the AxonServer java connector
 * @see <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer java connector</a>
 */
@Deprecated
public interface InstructionResultPublisher {

    /**
     * Notifies to Axon Server a successful execution of the specified instruction.
     *
     * @param instructionId the identifier of the instruction that has been successfully processed.
     */
    void publishSuccessFor(String instructionId);

    /**
     * Notifies to Axon Server a failure during the execution of the specified instruction.
     *
     * @param instructionId the identifier of the instruction.
     * @param error         the error happened during the instruction execution.
     */
    void publishFailureFor(String instructionId, Throwable error);

    /**
     * Notifies to Axon Server a failure during the execution of the specified instruction.
     *
     * @param instructionId    the identifier of the instruction.
     * @param errorDescription the description of the error happened during the instruction execution.
     */
    void publishFailureFor(String instructionId, String errorDescription);
}
