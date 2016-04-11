/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common;

import java.util.Iterator;
import java.util.Objects;

/**
 * Iterator that enables clients to peek at the next iteration result without advancing the iterator.
 * <p/>
 * Note that this iterator does not support the removal of elements.
 *
 * @param <E> the type of elements returned by this iterator
 * @author Rene de Waele
 */
public class PeekingIterator<E> implements Iterator<E> {

    private final Iterator<E> delegate;
    private boolean hasPeeked;
    private E peekElement;

    /**
     * Return a new instance of a PeekingIterator that provides elements from the given {@code iterator}.
     *
     * @param iterator  the iterator providing the elements for the returned iterator
     * @param <E>       the type of elements returned by this iterator
     * @return A PeekingIterator backed by given {@code iterator}.
     */
    public static <E> PeekingIterator<E> of(Iterator<E> iterator) {
        Objects.requireNonNull(iterator);
        if (iterator instanceof PeekingIterator) {
            return (PeekingIterator<E>) iterator;
        }
        return new PeekingIterator<>(iterator);
    }

    private PeekingIterator(Iterator<E> delegate) {
        this.delegate = delegate;
    }

    /**
     * Returns the next iteration result without advancing the iterator. If the Iterator has no more elements this
     * method returns {@code null}.
     *
     * @return the next element of the Iterator or {@code null} if the Iterator has no more elements.
     */
    public E peek() {
        if (!hasPeeked) {
            if (!hasNext()) {
                return null;
            }
            peekElement = delegate.next();
            hasPeeked = true;
        }
        return peekElement;
    }

    @Override
    public boolean hasNext() {
        return hasPeeked || delegate.hasNext();
    }

    @Override
    public E next() {
        if (!hasPeeked) {
            return delegate.next();
        }
        E result = peekElement;
        peekElement = null;
        hasPeeked = false;
        return result;
    }

    /**
     * This Iterator implementation does not support removing of elements.
     */
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
