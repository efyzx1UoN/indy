/**
 * Copyright (C) 2011-2022 Red Hat, Inc. (https://github.com/Commonjava/indy)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.indy.ftest.core.urls;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import org.commonjava.indy.model.core.dto.EndpointView;
import org.commonjava.indy.model.core.dto.EndpointViewListing;
import org.junit.Test;

public class CreateHostedStoreAndVerifyUrlInAllEndpointsTest
    extends AbstractCoreUrlsTest
{

    @Test
    public void verifyHostedStoreUrlsEndpoints()
        throws Exception
    {
        final EndpointViewListing endpoints = client.stats()
                                                    .getAllEndpoints();
        for ( final EndpointView endpoint : endpoints )
        {
            final String endpointUrl = client.content()
                                             .contentUrl( endpoint.getStoreKey() );

            assertThat( "Resource URI: '" + endpoint.getResourceUri() + "' for endpoint: " + endpoint.getKey()
                + " should be: '" + endpointUrl + "'", endpoint.getResourceUri(), equalTo( endpointUrl ) );
        }
    }

}
