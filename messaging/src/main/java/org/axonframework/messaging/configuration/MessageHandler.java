/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.configuration;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;

/**
 * TODO documentation
 * Interface for a component that processes Messages.
 *
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 3.0.o
 */
// TODO change this into a proper sealed interface by adding module information. Without, all interfaces need to reside in the same package, which we do not desire.
public /*sealed */interface MessageHandler/* permits CommandHandler, EventHandler, QueryHandler, MessageHandlingComponent */ {

}
