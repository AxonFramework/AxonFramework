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

package org.axonframework.integrationtests.testsuite.administration.commands;

import org.axonframework.integrationtests.testsuite.administration.common.PersonIdentifier;
import org.axonframework.modelling.annotation.TargetEntityId;

public record CreateEmployee(
        @TargetEntityId
        PersonIdentifier identifier,
        String emailAddress,
        String role,
        Double initialSalary
)  implements PersonCommand {

}
