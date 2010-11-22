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

package org.axonframework.saga;

import java.lang.reflect.InvocationTargetException;

/**
 * @author Allard Buijze
 * @since 0.7
 */
public class GenericSagaFactory implements SagaFactory {

    @Override
    public <T extends Saga> T createSaga(Class<T> sagaType) {
        try {
            return sagaType.getConstructor().newInstance();
        } catch (InstantiationException e) {
            throw new IllegalArgumentException(String.format("[%s] is not a suitable type for the GenericSagaFactory. "
                    + "It needs an accessible default constructor.", sagaType.getSimpleName()), e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(String.format("[%s] is not a suitable type for the GenericSagaFactory. "
                    + "The default constructor is not accessible.", sagaType.getSimpleName()), e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException(String.format("[%s] is not a suitable type for the GenericSagaFactory. "
                    + "An exception occurred while invoking the default constructor.", sagaType.getSimpleName()), e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(String.format("[%s] is not a suitable type for the GenericSagaFactory. "
                    + "There must be an accessible default (no-arg) constructor.", sagaType.getSimpleName()), e);
        }
    }
}
