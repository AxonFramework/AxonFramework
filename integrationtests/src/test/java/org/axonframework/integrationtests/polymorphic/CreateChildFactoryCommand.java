/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.integrationtests.polymorphic;

/**
 * The creational command that creates either child one or child two based on {@link #child} field. The command handler
 * is placed on abstract parent aggregate.
 *
 * @author Milan Savic
 */
public class CreateChildFactoryCommand {

    private final String id;
    private final int child;

    public CreateChildFactoryCommand(String id, int child) {
        this.id = id;
        this.child = child;
    }

    public String getId() {
        return id;
    }

    public int getChild() {
        return child;
    }
}
