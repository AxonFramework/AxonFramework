/*
 * Copyright (c) 2010-2015. Axon Framework
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

package org.axonframework.messaging.unitofwork;

import org.axonframework.messaging.ExecutionException;

import java.util.Objects;

/**
 * Class of objects that contain the result of an executed task.
 *
 * @author Rene de Waele
 */
public class ExecutionResult {

    private final Object result;

    /**
     * Initializes an {@link ExecutionResult} from the given {@code object}.
     *
     * @param result the result of an executed task
     */
    public ExecutionResult(Object result) {
        this.result = result;
    }

    /**
     * Returns the execution result. If the execution was completed successfully but yielded no result this method
     * returns {@code null}. If the execution gave rise to an exception, invoking this method will throw an
     * exception. Unchecked exceptions will be thrown directly. Checked exceptions are wrapped by a
     * {@link org.axonframework.messaging.ExecutionException}.
     *
     * @return The result of the execution if the operation was executed without raising an exception.
     */
    public Object getResult() {
        if (isExceptionResult()) {
            if (result instanceof RuntimeException) {
                throw (RuntimeException) result;
            }
            if (result instanceof Error) {
                throw (Error) result;
            }
            throw new ExecutionException("Execution of the task gave rise to an exception", (Throwable) result);
        }
        return result;
    }

    /**
     * Get the execution result in case the result is an exception. If the execution yielded no exception this method
     * returns {@code null}.
     *
     * @return The exception raised during execution of the task if any, {@code null} otherwise.
     */
    public Throwable getExceptionResult() {
        return isExceptionResult() ? (Throwable) result : null;
    }

    /**
     * Check if the result of the execution yielded an exception.
     *
     * @return {@code true} if execution of the task gave rise to an exception, {@code false} otherwise.
     */
    public boolean isExceptionResult() {
        return result instanceof Throwable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionResult that = (ExecutionResult) o;
        return Objects.equals(result, that.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(result);
    }

    @Override
    public String toString() {
        return String.format("ExecutionResult containing [%s]", result);
    }
}
