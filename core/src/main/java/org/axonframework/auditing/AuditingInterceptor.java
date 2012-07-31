/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.auditing;

import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.InterceptorChain;
import org.axonframework.unitofwork.UnitOfWork;

/**
 * Interceptor that keeps track of commands and the events that were dispatched as a result of handling that command.
 * The AuditingInterceptor uses the current Unit Of Work to track all events being stored and published. The auditing
 * information provided by the {@link org.axonframework.auditing.AuditDataProvider} is attached to each of these events
 * just before the Unit Of Work is committed. After the Unit Of Work has been committed, an optional {@link
 * org.axonframework.auditing.AuditLogger} may write an entry to the audit logs.
 * <p/>
 * Note that this class requires a Unit Of Work to be available. This means that you should always configure a Unit Of
 * Work interceptor <em>before</em> the AuditingInterceptor. Failure to do so will result in an {@link
 * org.axonframework.common.AxonConfigurationException}.
 * <p/>
 * The auditing interceptor can only attach meta data to event whose {@link
 * org.axonframework.domain.EventMessage#getMetaData()} methods returns an instance of {@link
 * org.axonframework.domain.MetaData}, which is the default behavior
 * for any event implementation provided by Axon.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class AuditingInterceptor implements CommandHandlerInterceptor {

    private AuditDataProvider auditDataProvider = EmptyDataProvider.INSTANCE;
    private AuditLogger auditLogger = NullAuditLogger.INSTANCE;

    @Override
    public Object handle(CommandMessage<?> command, UnitOfWork unitOfWork, InterceptorChain chain) throws Throwable {
        AuditingUnitOfWorkListener auditListener = new AuditingUnitOfWorkListener(command,
                                                                                  auditDataProvider,
                                                                                  auditLogger);
        unitOfWork.registerListener(auditListener);
        Object returnValue = chain.proceed();
        auditListener.setReturnValue(returnValue);
        return returnValue;
    }

    /**
     * Sets the AuditingDataProvider for this interceptor. Defaults to the {@link EmptyDataProvider}, which provides no
     * correlation information.
     *
     * @param auditDataProvider the instance providing the auditing information to attach to the events.
     */
    public void setAuditDataProvider(AuditDataProvider auditDataProvider) {
        this.auditDataProvider = auditDataProvider;
    }

    /**
     * Sets the logger that writes events to the audit logs. Defaults to the {@link
     * org.axonframework.auditing.NullAuditLogger}, which does nothing.
     *
     * @param auditLogger the logger that writes events to the audit logs
     */
    public void setAuditLogger(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }
}
