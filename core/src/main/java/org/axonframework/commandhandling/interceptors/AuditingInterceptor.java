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

import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.InterceptorChain;
import org.axonframework.messaging.unitofwork.UnitOfWork;

/**
 * Interceptor that keeps track of commands and the events that are dispatched as a result of handling that command.
 * <p/>
 * After a command message has been processed by the Unit of Work, an optional {@link AuditLogger} may write an
 * entry to the audit logs.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class AuditingInterceptor implements CommandHandlerInterceptor {

    private final AuditLogger auditLogger;

    public AuditingInterceptor(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @Override
    public Object handle(CommandMessage<?> command, UnitOfWork unitOfWork, InterceptorChain chain) throws Exception {
//        AuditingUnitOfWorkListener auditListener = new AuditingUnitOfWorkListener(command,
//                                                                                  auditDataProvider,
//                                                                                  auditLogger);
        // TODO: Seems that this whole class can be removed. We can use correlation data providers instead.
//        unitOfWork.registerListener(auditListener);
        Object returnValue = chain.proceed();
//        auditListener.setReturnValue(returnValue);
        return returnValue;
    }

}
