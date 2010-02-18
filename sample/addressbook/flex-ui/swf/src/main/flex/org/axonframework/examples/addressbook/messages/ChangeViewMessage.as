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

package org.axonframework.examples.addressbook.messages {
/**
 * Message used to change the view, The ID of the stack to switch to must be provided. Stack id's are available in the
 * ViewConstants component.
 */
public class ChangeViewMessage {
    public var stackId:String;

    public function ChangeViewMessage(stackId:String) {
        this.stackId = stackId;
    }
}
}