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

import org.springframework.util.Assert;

/**
 * <p>Create a new contact with the provided name</p>
 *
 * @author Jettro Coenradie
 */
public class CreateContactCommand extends AbstractOrderCommand {
    private String newContactName;

    /**
     * Set the name for the new Contact. An exception is thrown when the provided name is empty
     *
     * @param newContactName String containing the name for the new contact
     */
    public void setNewContactName(String newContactName) {
        Assert.hasText(newContactName, "Name for new contact must contain text");
        this.newContactName = newContactName;
    }

    /**
     * Returns the name for the new contact
     *
     * @return String containing the name for the new contact
     */
    public String getNewContactName() {
        return newContactName;
    }

}
