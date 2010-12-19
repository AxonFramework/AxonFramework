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

package org.axonframework.saga;

import java.lang.reflect.InvocationTargetException;

/**
 * SagaFactory implementation that uses the default (no-arg) constructor on the saga to initialize. After instantiation,
 * its resources are injected using an optional {@link #setResourceInjector(ResourceInjector) resource injector}.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class GenericSagaFactory implements SagaFactory {

    private ResourceInjector resourceInjector = NullResourceInjector.INSTANCE;

    @Override
    public <T extends Saga> T createSaga(Class<T> sagaType) {
        try {
            T instance = sagaType.getConstructor().newInstance();
            resourceInjector.injectResources(instance);
            return instance;
        } catch (InstantiationException e) {
            throw new IllegalArgumentException(String.format("[%s] is not a suitable type for the GenericSagaFactory. "
                                                                     + "It needs an accessible default constructor.",
                                                             sagaType.getSimpleName()), e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(String.format("[%s] is not a suitable type for the GenericSagaFactory. "
                                                                     + "The default constructor is not accessible.",
                                                             sagaType.getSimpleName()), e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException(String.format("[%s] is not a suitable type for the GenericSagaFactory. "
                                                                     + "An exception occurred while invoking the default constructor.",
                                                             sagaType.getSimpleName()), e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(String.format("[%s] is not a suitable type for the GenericSagaFactory. "
                                                                     + "There must be an accessible default (no-arg) constructor.",
                                                             sagaType.getSimpleName()), e);
        }
    }

    /**
     * Sets the resource injector that provides the resources needed by the Sagas created by this factory.
     *
     * @param resourceInjector The resource injector providing the necessary resources
     */
    public void setResourceInjector(ResourceInjector resourceInjector) {
        this.resourceInjector = resourceInjector;
    }

    private static class NullResourceInjector implements ResourceInjector {

        public static final NullResourceInjector INSTANCE = new NullResourceInjector();

        private NullResourceInjector() {
        }

        @Override
        public void injectResources(Saga saga) {
        }
    }
}
