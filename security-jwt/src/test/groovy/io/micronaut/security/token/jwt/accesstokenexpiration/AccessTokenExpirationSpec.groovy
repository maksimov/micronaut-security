/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.security.token.jwt.accesstokenexpiration

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.security.token.jwt.render.BearerAccessRefreshToken
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class AccessTokenExpirationSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            [
                    'spec.name': 'accesstokenexpiration',
                    'micronaut.security.enabled': true,
                    'endpoints.beans.enabled': true,
                    'endpoints.beans.sensitive': true,
                    'micronaut.security.endpoints.login.enabled': true,
                    'micronaut.security.token.jwt.enabled': true,
                    'micronaut.security.token.jwt.generator.access-token-expiration': 5,
                    'micronaut.security.token.jwt.signatures.secret.generator.secret': 'pleaseChangeThisSecretForANewOne'
            ], Environment.TEST)

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())


    def "expired access tokens failed validation"() {
        when:
        def creds = new UsernamePasswordCredentials('user', 'password')
        HttpResponse rsp = client.toBlocking().exchange(HttpRequest.POST('/login', creds), BearerAccessRefreshToken)

        then:
        rsp.status() == HttpStatus.OK
        rsp.body().accessToken

        when:
        final String accessToken =  rsp.body().accessToken
        HttpRequest request = HttpRequest.GET("/beans").header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        client.toBlocking().exchange(request)

        then:
        noExceptionThrown()

        when: 'sleep six seconds to leave time to the access token to expire'
        sleep(6_000)
        client.toBlocking().exchange(request)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }
}
