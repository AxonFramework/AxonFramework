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

package org.axonframework.integrationtests.eventstore.benchmark;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.util.Objects;

@Entity
public class PersistentObject {

    @Id
    @GeneratedValue
    private Long id;

    private String threadDescription;
    private int seqNo;

    public PersistentObject(String threadDescription, int seqNo) {
        this.threadDescription = threadDescription;
        this.seqNo = seqNo;
    }

    public PersistentObject() {
    }

    public Long getId() {
        return id;
    }

    public String getThreadDescription() {
        return threadDescription;
    }

    public int getSeqNo() {
        return seqNo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PersistentObject that = (PersistentObject) o;
        return Objects.equals(seqNo, that.seqNo) &&
                Objects.equals(id, that.id) &&
                Objects.equals(threadDescription, that.threadDescription);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, threadDescription, seqNo);
    }
}
