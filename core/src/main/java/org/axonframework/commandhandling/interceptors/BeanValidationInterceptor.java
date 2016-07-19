/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.commandhandling.interceptors;

import org.axonframework.commandhandling.CommandDispatchInterceptor;
import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.InterceptorChain;
import org.axonframework.unitofwork.UnitOfWork;

import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

/**
 * Interceptor that applies JSR303 bean validation on incoming commands. When validation on a command fails, a
 * JSR303ViolationException is thrown, holding the constraint violations.
 * <p/>
 * This interceptor can either be used as a {@link CommandHandlerInterceptor} or as a {@link
 * CommandDispatchInterceptor}.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class BeanValidationInterceptor implements CommandHandlerInterceptor, CommandDispatchInterceptor {

    private final ValidatorFactory validatorFactory;

    /**
     * Initializes a validation interceptor using a default ValidatorFactory (see {@link
     * javax.validation.Validation#buildDefaultValidatorFactory()}).
     */
    public BeanValidationInterceptor() {
        this(Validation.buildDefaultValidatorFactory());
    }

    /**
     * Initializes a validation interceptor using the given ValidatorFactory.
     *
     * @param validatorFactory the factory providing Validator instances for this interceptor.
     */
    public BeanValidationInterceptor(ValidatorFactory validatorFactory) {
        this.validatorFactory = validatorFactory;
    }

    @Override
    public Object handle(CommandMessage<?> command, UnitOfWork unitOfWork, InterceptorChain interceptorChain)
            throws Throwable {
        return interceptorChain.proceed(handle(command));
    }

    @Override
    public CommandMessage<?> handle(CommandMessage<?> command) {
        Validator validator = validatorFactory.getValidator();
        Set<ConstraintViolation<Object>> violations = validateCommand(command.getPayload(), validator);
        if (violations != null && !violations.isEmpty()) {
            throw new JSR303ViolationException(violations);
        }
        return command;
    }

    /**
     * Validate the given <code>command</code> using the given <code>validator</code>. The default implementation
     * merely calls <code>validator.validate(command)</code>.
     * <p/>
     * Subclasses may override this method to alter the validation behavior in specific cases. Although the
     * <code>null</code> is accepted as return value to indicate that there are no constraint violations,
     * implementations are encouraged to return an empty Set instead.
     *
     * @param command   The command to validate
     * @param validator The validator provided by the validator factory
     * @return a set of Constraint Violations. May also return <code>null</code>.
     */
    protected Set<ConstraintViolation<Object>> validateCommand(Object command, Validator validator) {
        return validator.validate(command);
    }
}
