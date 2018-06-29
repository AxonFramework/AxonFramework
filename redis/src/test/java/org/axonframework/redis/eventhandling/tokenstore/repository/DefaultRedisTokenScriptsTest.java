/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.redis.eventhandling.tokenstore.repository;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultRedisTokenScriptsTest {

    @Test
    public void testFetchTokenSha1() {
        assertThat(DigestUtils.sha1Hex(DefaultRedisTokenScripts.FETCH_TOKEN_SCRIPT)).isEqualTo(DefaultRedisTokenScripts.FETCH_TOKEN_SHA1);
    }

    @Test
    public void testStoreTokenSha1() {
        assertThat(DigestUtils.sha1Hex(DefaultRedisTokenScripts.STORE_TOKEN_SCRIPT)).isEqualTo(DefaultRedisTokenScripts.STORE_TOKEN_SHA1);
    }

    @Test
    public void testReleaseTokenSha1() {
        assertThat(DigestUtils.sha1Hex(DefaultRedisTokenScripts.RELEASE_TOKEN_SCRIPT)).isEqualTo(DefaultRedisTokenScripts.RELEASE_TOKEN_SHA1);
    }
}
