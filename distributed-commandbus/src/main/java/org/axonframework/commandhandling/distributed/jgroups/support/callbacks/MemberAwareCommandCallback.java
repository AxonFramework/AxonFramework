/*
 * Copyright (c) 2010-2015. Axon Framework
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

package org.axonframework.commandhandling.distributed.jgroups.support.callbacks;


import org.axonframework.commandhandling.CommandCallback;
import org.jgroups.Address;
import org.jgroups.View;

/**
 * Internal class used used by JGroupsConnector. For internal use only. Pulled outside to allow for seamless unit testing
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class MemberAwareCommandCallback<R> implements CommandCallback<R> {

    private final Address dest;
    private final CommandCallback<R> callback;

    public MemberAwareCommandCallback(Address dest, CommandCallback<R> callback) {
        this.dest = dest;
        this.callback = callback;
    }

    public boolean isMemberLive(View currentView) {
        return currentView.containsMember(dest);
    }

    @Override
    public void onSuccess(R result) {
        callback.onSuccess(result);
    }

    @Override
    public void onFailure(Throwable cause) {
        callback.onFailure(cause);
    }
}
