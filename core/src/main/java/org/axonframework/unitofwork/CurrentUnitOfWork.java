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

    private static final ThreadLocal<Deque<UnitOfWork>> CURRENT = new ThreadLocal<Deque<UnitOfWork>>() {
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
        return !CURRENT.get().isEmpty();
    }

    /**
     * Gets the UnitOfWork bound to the current thread. If no UnitOfWork has been started, an {@link
     * IllegalStateException} is thrown.
     * <p/>
     * To verify whether a UnitOfWork is already active, use {@link #isStarted()}.
     *
     * @return The UnitOfWork bound to the current thread.
     *
     * @throws IllegalStateException if no UnitOfWork is active
     */
    public static UnitOfWork get() {
        Deque<UnitOfWork> currentUnitOfWork = CURRENT.get();
        if (currentUnitOfWork.isEmpty()) {
            throw new IllegalStateException("No UnitOfWork is currently started for this thread.");
        }
        return currentUnitOfWork.peek();
    }

    /**
     * Commits the current UnitOfWork. If no UnitOfWork was started, an {@link IllegalStateException} is thrown.
     *
     * @throws IllegalStateException if no UnitOfWork is currently started.
     * @see org.axonframework.unitofwork.UnitOfWork#commit()
     */
    public static void commit() {
        if (!CurrentUnitOfWork.isStarted()) {
            throw new IllegalStateException("No UnitOfWork is currenly started");
        }
        CurrentUnitOfWork.get().commit();
    }

    /**
     * Binds the given <code>unitOfWork</code> to the current thread. If other UnitOfWork instances were bound, they
     * will be marked as inactive until the given UnitOfWork is cleared.
     *
     * @param unitOfWork The UnitOfWork to bind to the current thread.
     */
    static void set(UnitOfWork unitOfWork) {
        CURRENT.get().push(unitOfWork);
    }

    /**
     * Clears the UnitOfWork currently bound to the current thread, if that UnitOfWork is the given
     * <code>unitOfWork</code>. Otherwise, nothing happens.
     *
     * @param unitOfWork The UnitOfWork expected to be bound to the current thread.
     * @throws IllegalStateException when the given UnitOfWork was not the current active UnitOfWork. This exception
     *                               indicates a potentially wrong nesting of Units Of Work.
     */
    static void clear(UnitOfWork unitOfWork) {
        if (isStarted() && CURRENT.get().peek() == unitOfWork) {
            CURRENT.get().pop();
        } else {
            throw new IllegalStateException("Could not clear this UnitOfWork. It is not the active one.");
        }
    }
}
