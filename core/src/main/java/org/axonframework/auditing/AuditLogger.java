/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.auditing;

/**
 * Interface describing an instance capable of logging an auditing context to an external (usually persitent) source.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public interface AuditLogger {

    /**
     * Writes the contents of the auditing context to an external (usually persistent) source.
     * <p/>
     * Note that throwing (runtime) exceptions is highly discouraged, since command execution and event dispatching have
     * already taken place when the audit logger is invoked. Relying on a transactional interceptor to roll back command
     * and event processing is not recommended.
     *
     * @param context The auditing context containing the information to log.
     */
    void log(AuditingContext context);

}
