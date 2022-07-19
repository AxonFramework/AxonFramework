/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.serialization;

public class MessageSerializationPayload {
    private String myProperty1;
    private String getMyProperty2;

    public MessageSerializationPayload() {}

    public MessageSerializationPayload(String myProperty1, String getMyProperty2) {
        this.myProperty1 = myProperty1;
        this.getMyProperty2 = getMyProperty2;
    }

    public String getMyProperty1() {
        return myProperty1;
    }

    public void setMyProperty1(String myProperty1) {
        this.myProperty1 = myProperty1;
    }

    public String getGetMyProperty2() {
        return getMyProperty2;
    }

    public void setGetMyProperty2(String getMyProperty2) {
        this.getMyProperty2 = getMyProperty2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MessageSerializationPayload that = (MessageSerializationPayload) o;

        if (myProperty1 != null ? !myProperty1.equals(that.myProperty1) : that.myProperty1 != null) {
            return false;
        }
        return getMyProperty2 != null ? getMyProperty2.equals(that.getMyProperty2) : that.getMyProperty2 == null;
    }

    @Override
    public int hashCode() {
        int result = myProperty1 != null ? myProperty1.hashCode() : 0;
        result = 31 * result + (getMyProperty2 != null ? getMyProperty2.hashCode() : 0);
        return result;
    }
}
