/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.common.property;


/**
 * PropertyAccessStrategy implementation that finds properties defined according to the Uniform Access Principle
 * (see <a href="http://en.wikipedia.org/wiki/Uniform_access_principle">Wikipedia</a>).
 * For example, a property called {@code myProperty}, it will use a method called {@code myProperty()};
 *
 * @author Maxim Fedorov
 * @author Allard Buijze
 * @since 2.0
 */
public class UniformPropertyAccessStrategy extends AbstractMethodPropertyAccessStrategy {

    @Override
    protected String getterName(String property) {
        return property;
    }

    @Override
    protected int getPriority() {
        return -1024;
    }
}
