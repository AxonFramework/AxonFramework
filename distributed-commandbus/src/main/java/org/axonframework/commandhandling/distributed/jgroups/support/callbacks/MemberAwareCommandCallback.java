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
 * Callback implementation which wraps another callback, and is aware of the JGroups node responsible for providing the
 * value to invoke the wrapped callback with.
 *
 * @param <R> The expected type of return value
 * @author Allard Buijze
 * @since 2.0
 */
public class MemberAwareCommandCallback<R> implements CommandCallback<R> {

    private final Address dest;
    private final CommandCallback<R> callback;

    /**
     * Initialize the callback, where the given <code>dest</code> is responsible for providing the value to invoke the
     * given <code>callback</code> with.
     *
     * @param dest     The destination of the command of which the result is to be passed to the callback
     * @param callback The callback to invoke with the result of the command
     */
    public MemberAwareCommandCallback(Address dest, CommandCallback<R> callback) {
        this.dest = dest;
        this.callback = callback;
    }

    /**
     * Indicates whether the node responsible for providing the value to invoke the callback with is still alive in the
     * given <code>currentView</code>.
     *
     * @param view The view containing the currently available nodes in the JGroups cluster
     * @return <code>true</code> if the node is live, otherwise <code>false</code>
     */
    public boolean isMemberLive(View view) {
        return view.containsMember(dest);
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
