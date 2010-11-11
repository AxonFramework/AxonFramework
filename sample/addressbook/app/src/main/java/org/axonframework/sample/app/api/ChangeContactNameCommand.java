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
 * <p>Command that wants to change the name of the contact and requires the new contact name to do that</p>
 *
 * @author Jettro Coenradie
 */
public class ChangeContactNameCommand extends AbstractOrderCommand {
    private String contactNewName;

    /**
     * Returns the new name for the contact
     *
     * @return String containing the new name
     */
    public String getContactNewName() {
        return contactNewName;
    }

    /**
     * Provide the new name for the existing contact. An error is thrown if the provided name is empty
     *
     * @param contactNewName String containing the new name for the contact
     */
    public void setContactNewName(String contactNewName) {
        Assert.hasText(contactNewName, "New name for contact should contain text");
        this.contactNewName = contactNewName;
    }
}
