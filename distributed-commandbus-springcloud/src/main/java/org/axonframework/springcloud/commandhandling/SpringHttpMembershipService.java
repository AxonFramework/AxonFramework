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

package org.axonframework.springcloud.commandhandling;

import java.net.URI;
import java.util.Optional;
import java.util.function.Predicate;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * A Spring Http implementation of the {@link org.axonframework.springcloud.commandhandling.MembershipService}, which
 * uses Spring Web annotations to turn this class into a controller and a {@link org.springframework.web.client.RestTemplate}
 * to request {@link org.axonframework.springcloud.commandhandling.MembershipInformation} from other members.
 */
@RestController
@RequestMapping(SpringHttpMembershipService.MEMBERSHIP_INFORMATION_PATH)
public class SpringHttpMembershipService implements MembershipService {

    private static final Logger logger = LoggerFactory.getLogger(SpringHttpMembershipService.class);

    static final String MEMBERSHIP_INFORMATION_PATH = "/membership-information";

    private final RestTemplate restTemplate;
    private final Serializer serializer = new XStreamSerializer();

    private MembershipInformation membershipInformation;

    public SpringHttpMembershipService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void updateMembership(int loadFactor, Predicate<? super CommandMessage<?>> commandFilter) {
        membershipInformation = new MembershipInformation(loadFactor, commandFilter, serializer);
    }

    @GetMapping
    @Override
    public MembershipInformation getLocalMembershipInformation() {
        return membershipInformation;
    }

    @Override
    public MembershipInformation getRemoteMembershipInformation(Member remoteMember) {
        Optional<URI> optionalEndpoint = remoteMember.getConnectionEndpoint(URI.class);
        if (optionalEndpoint.isPresent()) {
            URI endpointUri = optionalEndpoint.get();
            URI destinationUri = buildURIForPath(endpointUri, MEMBERSHIP_INFORMATION_PATH);

            return restTemplate.exchange(destinationUri,
                                         HttpMethod.GET,
                                         new HttpEntity<>(null),
                                         MembershipInformation.class)
                               .getBody();
        } else {
            String errorMessage = String.format("No Connection Endpoint found in Member [%s] for protocol [%s] " +
                                                        "to send a MembershipInformation request to",
                                                remoteMember,
                                                URI.class);
            logger.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private static URI buildURIForPath(URI uri, String appendToPath) {
        return UriComponentsBuilder.fromUri(uri)
                                   .path(uri.getPath() + appendToPath)
                                   .build()
                                   .toUri();
    }

}
