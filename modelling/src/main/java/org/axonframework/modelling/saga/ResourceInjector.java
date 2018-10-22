/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.modelling.saga;

/**
 * Interface describing a mechanism to inject resources into Saga instances.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public interface ResourceInjector {

    /**
     * Inject required resources into the given {@code saga}.
     *
     * @param saga The saga to inject resources into
     */
    void injectResources(Object saga);

}
