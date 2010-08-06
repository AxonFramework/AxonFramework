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

package org.axonframework.unitofwork;

import java.util.Deque;
import java.util.LinkedList;

/**
 * Default entry point to gain access to the current UnitOfWork. Components managing transactional boundaries can
 * register and clear UnitOfWork instances, which components can use.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public abstract class CurrentUnitOfWork {

    private static final ThreadLocal<Deque<UnitOfWork>> current = new ThreadLocal<Deque<UnitOfWork>>() {
        @Override
        protected Deque<UnitOfWork> initialValue() {
            return new LinkedList<UnitOfWork>();
        }
    };

    private CurrentUnitOfWork() {
    }

    /**
     * Indicates whether a unit of work has already been started. This method can be used by interceptors to prevent
     * nesting of UnitOfWork instances.
     *
     * @return whether a UnitOfWork has already been started.
     */
    public static boolean isStarted() {
        return !current.get().isEmpty();
    }

    /**
     * Gets the UnitOfWork bound to the current thread. If no UnitOfWork has been registered with {@link
     * #set(UnitOfWork)}, an {@link ImplicitUnitOfWork} is returned.
     *
     * @return The UnitOfWork bound to the current thread.
     */
    public static UnitOfWork get() {
        Deque<UnitOfWork> currentUnitOfWork = current.get();
        if (currentUnitOfWork.isEmpty()) {
            set(new ImplicitUnitOfWork());
        }
        return currentUnitOfWork.peek();
    }

    /**
     * Binds the given <code>unitOfWork</code> to the current thread. If other UnitOfWork instances were bound, they
     * will be marked as inactive until the given UnitOfWork is cleared.
     *
     * @param unitOfWork The UnitOfWork to bind to the current thread.
     */
    public static void set(UnitOfWork unitOfWork) {
        current.get().push(unitOfWork);
    }

    /**
     * Clears the UnitOfWork currently bound to the current thread. The previously bound UnitOfWork, if any, is
     * restored.
     */
    public static void clear() {
        if (!current.get().isEmpty()) {
            current.get().pop();
        }
    }

    /**
     * Clears the UnitOfWork currently bound to the current thread, if that UnitOfWork is the given
     * <code>unitOfWork</code>. Otherwise, nothing happens.
     *
     * @param unitOfWork The UnitOfWork expected to be bound to the current thread.
     */
    public static void clear(UnitOfWork unitOfWork) {
        if (!current.get().isEmpty() && current.get().peek() == unitOfWork) {
            current.get().pop();
        }

    }

}
