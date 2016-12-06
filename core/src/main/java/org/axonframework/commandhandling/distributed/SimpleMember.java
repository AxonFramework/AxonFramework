/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.commandhandling.distributed;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * An endpoint in the network for a command handling service.
 *
 * @param <E> The type of the identifier of this entry in the set of nodes
 */
public class SimpleMember<E> implements Member {
    private final Consumer<SimpleMember<E>> suspectHandler;
    private final String name;
    private final E endpoint;

    /**
     * Create the service member
     *
     * @param name           the member name
     * @param endpoint       The object describing the endpoint
     * @param suspectHandler The operation to execute when this member is marked as a suspect
     */
    public SimpleMember(String name, E endpoint, Consumer<SimpleMember<E>> suspectHandler) {
        this.name = name;
        this.endpoint = endpoint;
        this.suspectHandler = suspectHandler;
    }

    @Override
    public String name() {
        return name;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> getConnectionEndpoint(Class<T> protocol) {
        if (protocol.isInstance(endpoint)) {
            return Optional.of((T) endpoint);
        }
        return Optional.empty();
    }

    @Override
    public void suspect() {
        if (suspectHandler != null) {
            suspectHandler.accept(this);
        }
    }

    /**
     * Returns this Member's endpoint.
     *
     * @return the endpoint of this member
     */
    public E endpoint() {
        return endpoint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SimpleMember<?> that = (SimpleMember<?>) o;
        return Objects.equals(name, that.name) && Objects.equals(endpoint, that.endpoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, endpoint);
    }

    @Override
    public String toString() {
        return "SimpleMember{" + "name=" + name + ", endpoint=" + endpoint + '}';
    }
}
