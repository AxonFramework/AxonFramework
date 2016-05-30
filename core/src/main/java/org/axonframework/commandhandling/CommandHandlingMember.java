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

package org.axonframework.commandhandling;

import java.lang.annotation.*;

/**
 * Marker annotation for field that contain an Entity capable of handling Commands on behalf of the aggregate. When
 * a field is annotated with <code>@CommandHandlerMember</code>, it is a hint towards Command Handler discovery
 * mechanisms that the entity should also be inspected for {@link CommandHandler @CommandHandler} annotated methods.
 * <p/>
 * Note that CommandHandler detection is done using static typing. This means that only the declared type of the field
 * can be inspected. If a subclass of that type is assigned to the field, any handlers declared on that subclass will
 * be ignored.
 *
 * @author Allard Buijze
 * @since 2.2
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface CommandHandlingMember {

}
