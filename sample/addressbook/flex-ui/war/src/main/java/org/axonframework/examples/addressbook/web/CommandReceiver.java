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

package org.axonframework.examples.addressbook.web;

/**
 * <p>Generic command receiver which accepts all commands. By calling the sendCommand method you expect the command
 * to be put on the CommandBus</p>
 * @author Jettro Coenradie
 */
public interface CommandReceiver {
    /**
     * Send a new command to the command bus. If a return value is available it is passed on.
     * @param command Object representing the command to be dispatched to the command bus
     * @return Object as returned by the command bus
     */
    Object sendCommand(Object command);
}
