/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.saga.repository;

import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.annotation.AbstractAnnotatedSaga;

/**
* @author Allard Buijze
*/
public class StubSaga extends AbstractAnnotatedSaga {

    private static final long serialVersionUID = -1562911263884220240L;

    public StubSaga(){ }

    public StubSaga(String identifier) {
        super(identifier);
    }

    public void registerAssociationValue(AssociationValue associationValue) {
        associateWith(associationValue);
    }

    public void removeAssociationValue(String key, String value) {
        removeAssociationWith(key, value);
    }

    @Override
    public void end() {
        super.end();
    }

    public void associate(String key, String value) {
        associateWith(key, value);
    }
}
