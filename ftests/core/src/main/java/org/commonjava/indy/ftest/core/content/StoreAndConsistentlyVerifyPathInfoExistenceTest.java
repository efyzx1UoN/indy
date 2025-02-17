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
package org.commonjava.indy.ftest.core.content;

import static org.commonjava.indy.model.core.StoreType.hosted;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.commonjava.indy.client.core.helper.PathInfo;
import org.commonjava.indy.ftest.core.AbstractContentManagementTest;
import org.commonjava.indy.test.fixture.core.CoreServerFixture;
import org.junit.Test;

public class StoreAndConsistentlyVerifyPathInfoExistenceTest
        extends AbstractContentManagementTest
{
    private final int PASSTHROUGH_TIMEOUT_SECONDS = 9;

    @Test
    public void storeAndVerifyPathInfo_10Times()
        throws Exception
    {
        final InputStream stream = new ByteArrayInputStream( ( "This is a test: " + System.nanoTime() ).getBytes() );

        final String path = "/path/to/foo.class";
        client.content()
              .store( hosted, STORE, path, stream );

        for ( int i = 0; i < 10; i++ )
        {
            final PathInfo result = client.content()
                                          .getInfo( hosted, STORE, path );
            assertThat( "pass: " + i + "...no result", result, notNullValue() );
            assertThat( "pass: " + i + "...doesn't exist", result.exists(), equalTo( true ) );
        }
    }

    @Override
    protected int getTestTimeoutMultiplier()
    {
        return 2;
    }

    @Override
    protected void initTestConfig( CoreServerFixture fixture )
            throws IOException
    {
        writeConfigFile( "main.conf", readTestResource( "default-test-main.conf" ) );
    }
}
