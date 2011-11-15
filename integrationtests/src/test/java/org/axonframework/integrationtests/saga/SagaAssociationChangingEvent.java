/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.integrationtests.saga;

/**
 * @author Allard Buijze
 */
public class SagaAssociationChangingEvent {

    private final String currentAssociationValue;
    private final String newAssociationValue;

    /**
     * Initialize an application event with the given <code>source</code>. Source may be null. In that case, the source
     * type and source description will be set to <code>Object.class</code> and <code>[unknown source]</code>
     * respectively.
     */
    protected SagaAssociationChangingEvent(String currentAssociationValue, String newAssociationValue) {
        this.currentAssociationValue = currentAssociationValue;
        this.newAssociationValue = newAssociationValue;
    }

    public String getCurrentAssociation() {
        return currentAssociationValue;
    }

    public String getNewAssociation() {
        return newAssociationValue;
    }
}
