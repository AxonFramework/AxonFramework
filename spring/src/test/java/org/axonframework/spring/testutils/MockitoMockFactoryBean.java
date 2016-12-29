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

package org.axonframework.spring.testutils;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Required;

import static org.mockito.Mockito.mock;

/**
 * @author Allard Buijze
 */
public class MockitoMockFactoryBean<T> implements FactoryBean<T> {

    private Class<T> mockType;
    private boolean singleton = true;
    private T lastMock;

    @Override
    public T getObject() throws Exception {
        if (singleton && lastMock != null) {
            return lastMock;
        }
        lastMock = mock(mockType);
        return lastMock;
    }

    @Override
    public Class<T> getObjectType() {
        return mockType;
    }

    @Override
    public boolean isSingleton() {
        return singleton;
    }

    @Required
    public void setMockType(Class<T> mockType) {
        this.mockType = mockType;
    }

    public void setSingleton(boolean singleton) {
        this.singleton = singleton;
    }
}
