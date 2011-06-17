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

/**
 * <p>Repository used to manage unique contact names</p>
 *
 * @author Jettro Coenradie
 */
public interface ContactNameRepository {

    /**
     * Claims the provided contact name, if the name is not available anymore false is returned
     *
     * @param contactName String containing the contact name to claim
     * @return boolean indicating whether the claim was successful
     */
    boolean claimContactName(String contactName);

    /**
     * Release the claim for the provided name
     *
     * @param contactName String containing the name to release the claim for
     */
    void cancelContactName(String contactName);

    /**
     * Returns true if the contact name is available, and false if it has been taken.
     *
     * @param contactName String containing the contact name to check for vacancy
     * @return true if the provided contact name has not been taken, false otherwise
     */
    boolean vacantContactName(String contactName);
}
