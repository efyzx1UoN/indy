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
package org.commonjava.indy.pkg.maven.content;

import org.commonjava.indy.client.core.IndyClientException;
import org.commonjava.indy.ftest.core.AbstractContentManagementTest;
import org.commonjava.indy.model.core.Group;
import org.commonjava.indy.model.core.HostedRepository;
import org.commonjava.indy.model.core.RemoteRepository;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Check that the group's merged metadata is generated then checksummed when the checksum is requested BEFORE the
 * metadata has been merged.
 * <br/>
 * GIVEN:
 * <ul>
 *     <li>HostedRepositories A and B</li>
 *     <li>Group G with members remote:A and hosted:B</li>
 *     <li>RemoteRepository A contains invalid connection configuration that will result in status 401
 *          for all requests.</li>
 *     <li>HostedRepository B contains a valid Path P metadata file.</li>
 *     <li>Path P has not been requested from Group G yet</li>
 * </ul>
 * <br/>
 * WHEN:
 * <ul>
 *     <li>Path P is requested from Group G</li>
 * </ul>
 * <br/>
 * THEN:
 * <ul>
 *     <li>Indy will ignore the 401 response from RemoteRepository A for path P.</li>
 *     <li>Group G will return the metadata from HostedRepository B for path P.</li>
 * </ul>
 */
public class GroupMetadata401ErrorTest
        extends AbstractContentManagementTest
{
    private static final String GROUP_G_NAME= "G";
    private static final String REMOTE_A_NAME = "A";
    private static final String HOSTED_B_NAME= "B";

    private static final String B_VERSION = "1.0";

    private static final String METADATA_PATH = "/org/foo/bar/maven-metadata.xml";

    /* @formatter:off */
    private static final String REPO_METADATA_TEMPLATE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<metadata>\n" +
        "  <groupId>org.foo</groupId>\n" +
        "  <artifactId>bar</artifactId>\n" +
        "  <versioning>\n" +
        "    <latest>%version%</latest>\n" +
        "    <release>%version%</release>\n" +
        "    <versions>\n" +
        "      <version>%version%</version>\n" +
        "    </versions>\n" +
        "    <lastUpdated>20150722164334</lastUpdated>\n" +
        "  </versioning>\n" +
        "</metadata>\n";
    /* @formatter:on */

    /* @formatter:off */
    private static final String GROUP_METADATA_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<metadata>\n" +
        "  <groupId>org.foo</groupId>\n" +
        "  <artifactId>bar</artifactId>\n" +
        "  <versioning>\n" +
        "    <latest>1.0</latest>\n" +
        "    <release>1.0</release>\n" +
        "    <versions>\n" +
        "      <version>1.0</version>\n" +
        "    </versions>\n" +
        "    <lastUpdated>20150722164334</lastUpdated>\n" +
        "  </versioning>\n" +
        "</metadata>\n";
    /* @formatter:on */

    private Group g;

    private RemoteRepository a;
    private HostedRepository b;

    @Before
    public void setupRepos()
            throws Exception
    {
        String message = "test setup";

        a = client.stores()
                  .create( new RemoteRepository( REMOTE_A_NAME, server.formatUrl( REMOTE_A_NAME ) ), message,
                           RemoteRepository.class );

        server.expect( "GET", server.formatUrl( REMOTE_A_NAME, METADATA_PATH ), 401, "Unauthorized." );

        b = client.stores().create( new HostedRepository( HOSTED_B_NAME ), message, HostedRepository.class );

        g = client.stores().create( new Group( GROUP_G_NAME, a.getKey(), b.getKey() ), message, Group.class );

        deployContent( b, METADATA_PATH, REPO_METADATA_TEMPLATE, B_VERSION );
    }

    @Test
    public void run()
            throws Exception
    {
        String metadataContent = assertContent( g, METADATA_PATH, GROUP_METADATA_CONTENT );
    }

    @Override
    protected boolean createStandardTestStructures()
    {
        return false;
    }
}
