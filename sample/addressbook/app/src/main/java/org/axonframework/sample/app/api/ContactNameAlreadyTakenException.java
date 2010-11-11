/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.sample.app.api;

/**
 * <p>Exception thrown when the name you are trying to use for a change or a new contact is already taken</p>
 *
 * @author Jettro Coenradie
 */
public class ContactNameAlreadyTakenException extends RuntimeException {
    /**
     * Accepts the name of the contact that you tried to take for a change or a new contact
     *
     * @param newContactName String containing the name you tried to take
     */
    public ContactNameAlreadyTakenException(String newContactName) {
        super(newContactName);
    }
}
