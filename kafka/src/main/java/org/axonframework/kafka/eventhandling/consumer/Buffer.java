/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.kafka.eventhandling.consumer;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Defines a buffer that wait for the space to become non-empty when retrieving an
 * element, and wait for space to become available in the buffer when
 * storing an element.
 *
 * @param <E>
 * @author Nakul Mishra
 */
public interface Buffer<E> {

    void put(E e) throws InterruptedException;

    void putAll(Collection<E> c) throws InterruptedException;

    E poll(long timeout, TimeUnit unit) throws InterruptedException;

    E take() throws InterruptedException;

    E peek();

    int size();

    boolean isEmpty();

    int remainingCapacity();

    void clear();
}
