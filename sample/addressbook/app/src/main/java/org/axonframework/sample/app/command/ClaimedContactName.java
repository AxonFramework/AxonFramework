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

package org.axonframework.sample.app.command;

import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * <p>Entity used for maintaining the contact names that have already been taken</p>
 *
 * @author Jettro Coenradie
 */
@Entity
public class ClaimedContactName {

    @Id
    private String contactName;

    public ClaimedContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactName() {
        return contactName;
    }

    /**
     * Required for jpa
     */
    public ClaimedContactName() {
    }
}
