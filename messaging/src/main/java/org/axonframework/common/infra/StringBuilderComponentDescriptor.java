/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.common.infra;

import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.Map;

/**
 * A {@link ComponentDescriptor} implementation that uses a {@link StringBuilder} to construct the result.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class StringBuilderComponentDescriptor implements ComponentDescriptor {

    private final StringBuilder description = new StringBuilder();

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Object object) {

    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Collection<?> collection) {

    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Map<?, ?> map) {

    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull String value) {
        description.append(name)
                   .append(" : ")
                   .append(value);
    }

    @Override
    public void describeProperty(@Nonnull String name, long value) {
        description.append(name)
                   .append(" : ")
                   .append(value);
    }

    @Override
    public void describeProperty(@Nonnull String name, boolean value) {
        description.append(name)
                   .append(" : ")
                   .append(value);
    }

    @Override
    public String describe() {
        return description.toString();
    }
}
