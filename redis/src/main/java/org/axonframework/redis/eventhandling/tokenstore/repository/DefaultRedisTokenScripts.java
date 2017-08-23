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

/**
 * Redis lua scripts allowing for single round trip to Redis when fetching, storing or releasing a token.
 *
 * @author Michael Willemse
 */
public class DefaultRedisTokenScripts {

        public static final String FETCH_TOKEN_SCRIPT =
                "local processorNameSegment = KEYS[1]\n" +
                        "local processorName = ARGV[1]\n" +
                        "local segment = ARGV[2]\n" +
                        "local owner = ARGV[3]\n" +
                        "local timestamp = ARGV[4]\n" +
                        "local expirationFromTimestamp = ARGV[5]\n" +
                        "\n" +
                        "local tokenEntry = {}\n" +
                        "\n" +
                        "local rawTokenHash = redis.call('HGETALL', processorNameSegment)\n" +
                        "\n" +
                        "for idx = 1, #rawTokenHash, 2 do\n" +
                        "    tokenEntry[rawTokenHash[idx]] = rawTokenHash[idx + 1]\n" +
                        "end\n" +
                        "\n" +
                        "if tokenEntry.processorName == processorName and tokenEntry.segment == segment then\n" +
                        "    if tokenEntry.owner == nil or tokenEntry.owner == owner or expirationFromTimestamp > tokenEntry.timestamp then\n" +
                        "        tokenEntry.owner = owner\n" +
                        "        tokenEntry.timestamp = timestamp\n" +
                        "        redis.call('HMSET', processorNameSegment, 'owner', tokenEntry.owner, 'timestamp', tokenEntry.timestamp)\n" +
                        "    end\n" +
                        "else\n" +
                        "    tokenEntry.processorName = processorName\n" +
                        "    tokenEntry.segment = segment\n" +
                        "    tokenEntry.owner = owner\n" +
                        "    tokenEntry.timestamp = timestamp\n" +
                        "    redis.call('HMSET', processorNameSegment, 'processorName', tokenEntry.processorName, 'segment', tokenEntry.segment, 'owner', tokenEntry.owner, 'timestamp', tokenEntry.timestamp)\n" +
                        "end\n" +
                        "\n" +
                        "return cjson.encode(tokenEntry)\n";

        public static final String FETCH_TOKEN_SHA1 = "548196ccbcf749d06b31f0d6be7a7f23055b5e1f";

        public static final String STORE_TOKEN_SCRIPT =
                "local processorNameSegment = KEYS[1]\n" +
                        "local processorName = ARGV[1]\n" +
                        "local segment = ARGV[2]\n" +
                        "local owner = ARGV[3]\n" +
                        "local timestamp = ARGV[4]\n" +
                        "local expirationFromTimestamp = ARGV[5]\n" +
                        "local token = ARGV[6]\n" +
                        "local tokenType = ARGV[7]\n" +
                        "\n" +
                        "local tokenEntry = {}\n" +
                        "\n" +
                        "local rawTokenHash = redis.call('HGETALL', processorNameSegment)\n" +
                        "\n" +
                        "for idx = 1, #rawTokenHash, 2 do\n" +
                        "    tokenEntry[rawTokenHash[idx]] = rawTokenHash[idx + 1]\n" +
                        "end\n" +
                        "\n" +
                        "if tokenEntry.processorName == processorName and tokenEntry.segment == segment then\n" +
                        "    if tokenEntry.owner == nil or tokenEntry.owner == owner or expirationFromTimestamp > tokenEntry.timestamp then\n" +
                        "        tokenEntry.owner = owner\n" +
                        "        tokenEntry.timestamp = timestamp\n" +
                        "        tokenEntry.token = token\n" +
                        "        tokenEntry.tokenType = tokenType\n" +
                        "        redis.call('HMSET', processorNameSegment, 'owner', tokenEntry.owner, 'timestamp', tokenEntry.timestamp, 'token', tokenEntry.token, 'tokenType', tokenEntry.tokenType)\n" +
                        "    end\n" +
                        "else\n" +
                        "    tokenEntry.processorName = processorName\n" +
                        "    tokenEntry.segment = segment\n" +
                        "    tokenEntry.owner = owner\n" +
                        "    tokenEntry.timestamp = timestamp\n" +
                        "    tokenEntry.token = token\n" +
                        "    tokenEntry.tokenType = tokenType\n" +
                        "    redis.call('HMSET', processorNameSegment, 'processorName', tokenEntry.processorName, 'segment', tokenEntry.segment, 'owner', tokenEntry.owner, 'timestamp', tokenEntry.timestamp, 'token', tokenEntry.token, 'tokenType', tokenEntry.tokenType)\n" +
                        "end\n" +
                        "\n" +
                        "return cjson.encode(tokenEntry)\n";

        public static final String STORE_TOKEN_SHA1 = "eab683f2814c41c0ebdf57ce13fe9ce6b97eba1e";

        public static final String RELEASE_TOKEN_SCRIPT = "" +
                "local processorNameSegment = KEYS[1]\n" +
                "local processorName = ARGV[1]\n" +
                "local segment = ARGV[2]\n" +
                "local owner = ARGV[3]\n" +
                "\n" +
                "local tokenEntry = {}\n" +
                "\n" +
                "local rawTokenHash = redis.call('HGETALL', processorNameSegment)\n" +
                "\n" +
                "for idx = 1, #rawTokenHash, 2 do\n" +
                "    tokenEntry[rawTokenHash[idx]] = rawTokenHash[idx + 1]\n" +
                "end\n" +
                "\n" +
                "if tokenEntry.processorName == processorName and tokenEntry.segment == segment and tokenEntry.owner == owner then\n" +
                "    redis.call('HDEL', processorNameSegment, 'owner')\n" +
                "    return true\n" +
                "else\n" +
                "    return false\n" +
                "end\n";

        public static final String RELEASE_TOKEN_SHA1 = "0fd1cf0e64fc1f7e4510c578fc861151da691db9";
}
