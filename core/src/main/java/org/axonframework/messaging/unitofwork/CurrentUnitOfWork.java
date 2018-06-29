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

package org.axonframework.messaging.unitofwork;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.slf4j.MDC;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Default entry point to gain access to the current UnitOfWork. Components managing transactional boundaries can
 * register and clear UnitOfWork instances, which components can use.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public abstract class CurrentUnitOfWork {

    private static final ThreadLocal<Deque<UnitOfWork<?>>> CURRENT = new ThreadLocal<>();

    /**
     * Indicates whether a unit of work has already been started. This method can be used by interceptors to prevent
     * nesting of UnitOfWork instances.
     *
     * @return whether a UnitOfWork has already been started.
     */
    public static boolean isStarted() {
        return CURRENT.get() != null && !CURRENT.get().isEmpty();
    }

    /**
     * If a UnitOfWork is started, invokes the given {@code consumer} with the active Unit of Work. Otherwise,
     * it does nothing
     *
     * @param consumer The consumer to invoke if a Unit of Work is active
     * @return {@code true} if a unit of work is active, {@code false} otherwise
     */
    public static boolean ifStarted(Consumer<UnitOfWork<?>> consumer) {
        if (isStarted()) {
            consumer.accept(get());
            return true;
        }
        return false;
    }

    /**
     * If a Unit of Work is started, execute the given {@code function} on it. Otherwise, returns an empty Optional.
     * Use this method when you wish to retrieve information from a Unit of Work, reverting to a default when no Unit
     * of Work is started.
     *
     * @param function The function to apply to the unit of work, if present
     * @param <T>      The type of return value expected
     * @return an optional containing the result of the function, or an empty Optional when no Unit of Work was started
     * @throws NullPointerException when a Unit of Work is present and the function returns null
     */
    public static <T> Optional<T> map(Function<UnitOfWork<?>, T> function) {
        return isStarted() ? Optional.of(function.apply(get())) : Optional.empty();
    }

    /**
     * Gets the UnitOfWork bound to the current thread. If no UnitOfWork has been started, an {@link
     * IllegalStateException} is thrown.
     * <p/>
     * To verify whether a UnitOfWork is already active, use {@link #isStarted()}.
     *
     * @return The UnitOfWork bound to the current thread.
     * @throws IllegalStateException if no UnitOfWork is active
     */
    public static UnitOfWork<?> get() {
        if (isEmpty()) {
            throw new IllegalStateException("No UnitOfWork is currently started for this thread.");
        }
        return CURRENT.get().peek();
    }

    private static boolean isEmpty() {
        Deque<UnitOfWork<?>> unitsOfWork = CURRENT.get();
        return unitsOfWork == null || unitsOfWork.isEmpty();
    }

    /**
     * Commits the current UnitOfWork. If no UnitOfWork was started, an {@link IllegalStateException} is thrown.
     *
     * @throws IllegalStateException if no UnitOfWork is currently started.
     * @see UnitOfWork#commit()
     */
    public static void commit() {
        get().commit();
    }

    /**
     * Binds the given {@code unitOfWork} to the current thread. If other UnitOfWork instances were bound, they
     * will be marked as inactive until the given UnitOfWork is cleared. Sets all metadata key/value
     * pairs for this {@code unitOfWork} on the {@link MDC} to make them available for logging.
     *
     * @param unitOfWork The UnitOfWork to bind to the current thread.
     */
    public static void set(UnitOfWork<?> unitOfWork) {
        if (CURRENT.get() == null) {
            CURRENT.set(new LinkedList<>());
        }

        UnitOfWork<?> previousUnitOfWork = CURRENT.get().peek();
        if (previousUnitOfWork != null) {
            clearActiveMetaDataLoggingContext(extractMetaData(previousUnitOfWork));
        }

        CURRENT.get().push(unitOfWork);
        setActiveMetaDataLoggingContext(extractMetaData(unitOfWork));
    }

    /**
     * Clears the UnitOfWork currently bound to the current thread, if that UnitOfWork is the given
     * {@code unitOfWork}. Also clears the given metadata for this {@code unitOfWork} from the
     * logging {@link MDC} and replaces them with the metadata of the previous unit of work, if
     * it exists.
     *
     * @param unitOfWork The UnitOfWork expected to be bound to the current thread.
     * @throws IllegalStateException when the given UnitOfWork was not the current active UnitOfWork. This exception
     *                               indicates a potentially wrong nesting of Units Of Work.
     */
    public static void clear(UnitOfWork<?> unitOfWork) {
        if (!isStarted()) {
            throw new IllegalStateException("Could not clear this UnitOfWork. There is no UnitOfWork active.");
        }
        if (CURRENT.get().peek() == unitOfWork) {
            CURRENT.get().pop();
            clearActiveMetaDataLoggingContext(extractMetaData(unitOfWork));

            if (CURRENT.get().isEmpty()) {
                CURRENT.remove();
            } else {
                UnitOfWork<?> nextUnitOfWork = CURRENT.get().peek();
                setActiveMetaDataLoggingContext(extractMetaData(nextUnitOfWork));
            }
        } else {
            throw new IllegalStateException("Could not clear this UnitOfWork. It is not the active one.");
        }
    }

    /**
     * Returns the Correlation Data attached to the current Unit of Work, or an empty {@link MetaData} instance
     * if no Unit of Work is started.
     *
     * @return a MetaData instance representing the current Unit of Work's correlation data, or an empty MetaData
     * instance if no Unit of Work is started.
     * @see UnitOfWork#getCorrelationData()
     */
    public static MetaData correlationData() {
        return CurrentUnitOfWork.map(UnitOfWork::getCorrelationData).orElse(MetaData.emptyInstance());
    }

    private CurrentUnitOfWork() {
    }

    private static void setActiveMetaDataLoggingContext(MetaData metaData) {
        metaData.forEach((k, v) -> MDC.put(k, v != null ? v.toString() : "null"));
    }

    private static void clearActiveMetaDataLoggingContext(MetaData metaData) {
        metaData.forEach((k, v) -> MDC.remove(k));
    }

    /**
     * Extracts {@code MetaData} nested in a {@code uow}. There is no nullity check on the
     * {@code uow} parameter as a unit of work cannot be null when it sets/removes itself as the
     * current unit of work.
     *
     * @param uow A {@link UnitOfWork} instance to examine for metadata
     * @return    The {@link MetaData} attached to this unit of work if it exists, otherwise an
     *            empty instance
     */
    private static MetaData extractMetaData(UnitOfWork<?> uow) {
        Message<?> message = uow.getMessage();
        if (message != null) {
            MetaData metaData = message.getMetaData();
            if (metaData != null) {
                return metaData;
            }
        }
        return MetaData.emptyInstance();
    }
}
