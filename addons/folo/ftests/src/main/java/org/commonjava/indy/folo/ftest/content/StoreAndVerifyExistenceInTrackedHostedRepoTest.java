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
package org.commonjava.indy.folo.ftest.content;

import static org.commonjava.indy.model.core.StoreType.hosted;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.commonjava.indy.folo.client.IndyFoloContentClientModule;
import org.junit.Test;

public class StoreAndVerifyExistenceInTrackedHostedRepoTest
    extends AbstractFoloContentManagementTest
{

    @Test
    public void storeFileAndVerifyExistence()
        throws Exception
    {
        final String trackingId = newName();
        
        final InputStream stream = new ByteArrayInputStream( ( "This is a test: " + System.nanoTime() ).getBytes() );

        final String path = "/path/to/foo.class";

        assertThat( client.module( IndyFoloContentClientModule.class )
                          .exists( trackingId, hosted, STORE, path ), equalTo( false ) );

        client.module( IndyFoloContentClientModule.class )
              .store( trackingId, hosted, STORE, path, stream );

        assertThat( client.module( IndyFoloContentClientModule.class )
                          .exists( trackingId, hosted, STORE, path ), equalTo( true ) );
    }

}
