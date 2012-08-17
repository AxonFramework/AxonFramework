/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.io;

/**
 * @author Allard Buijze
 */
public enum DefaultMessageSegments implements MessageSegment {

    UTF(-2),
    BINARY(-4),
    BYTE(1),
    INTEGER(4),
    LONG(8),
    SHORT(2);

    private final int segmentLength;

    DefaultMessageSegments(int segmentLength) {
        this.segmentLength = segmentLength;
    }

    @Override
    public boolean isFixedLength() {
        return segmentLength > 0;
    }

    @Override
    public int getPrefixSize() {
        if (segmentLength < 0) {
            return -segmentLength;
        } else {
            return 0;
        }
    }

    @Override
    public int getSegmentLength() {
        if (!isFixedLength()) {
            throw new IllegalStateException("This element is not of fixed length");
        }
        return segmentLength;
    }
}
