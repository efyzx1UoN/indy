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

import static org.commonjava.indy.model.core.StoreType.group;
import static org.commonjava.indy.model.core.StoreType.hosted;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayInputStream;

import org.apache.commons.lang3.StringUtils;
import org.commonjava.indy.ftest.core.AbstractContentManagementTest;
import org.commonjava.indy.model.core.Group;
import org.commonjava.indy.model.core.StoreKey;
import org.junit.Test;

public class StoreTwoFilesAndVerifyPresenceInGroupTest
        extends AbstractContentManagementTest
{

    @Test
    public void storeTwoFilesInConstituentAndVerifyExistenceInGroup()
        throws Exception
    {
        final Group g = client.stores()
                              .load( group, PUBLIC, Group.class );

        System.out.printf( "\n\nGroup constituents are:\n  %s\n\n", StringUtils.join( g.getConstituents(), "\n  " ) );

        assertThat( g.getConstituents()
                     .contains( new StoreKey( hosted, STORE ) ), equalTo( true ) );

        final String path = "/path/to/foo.txt";
        final String path2 = "/path/to/foo.txt";

        assertThat( client.content()
                          .exists( hosted, STORE, path ), equalTo( false ) );

        assertThat( client.content()
                          .exists( hosted, STORE, path2 ), equalTo( false ) );

        client.content()
              .store( hosted, STORE, path, new ByteArrayInputStream( "This is a test".getBytes() ) );

        client.content()
              .store( hosted, STORE, path2, new ByteArrayInputStream( "This is a test".getBytes() ) );

        assertThat( client.content()
                          .exists( group, PUBLIC, path ), equalTo( true ) );

        assertThat( client.content()
                          .exists( group, PUBLIC, path2 ), equalTo( true ) );
    }
}
