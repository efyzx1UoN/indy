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
package org.commonjava.indy.promote.ftest;

import org.commonjava.indy.client.core.IndyClientException;
import org.commonjava.indy.client.core.IndyClientModule;
import org.commonjava.indy.ftest.core.AbstractContentManagementTest;
import org.commonjava.indy.ftest.core.category.EventDependent;
import org.commonjava.indy.model.core.Group;
import org.commonjava.indy.model.core.HostedRepository;
import org.commonjava.indy.promote.client.IndyPromoteClientModule;
import org.commonjava.indy.promote.model.PathsPromoteRequest;
import org.commonjava.indy.promote.model.PathsPromoteResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Collections;

import static org.commonjava.indy.pkg.PackageTypeConstants.PKG_TYPE_MAVEN;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Check that metadata in a hosted repo is merged with content from a new hosted repository when it is promoted by path.
 * <br/>
 * GIVEN:
 * <ul>
 *     <li>HostedRepositories A and B</li>
 *     <li>HostedRepositories A and B both contain metadata path P, and pom path P2</li>
 *     <li>Each metadata file contains different versions of the same project</li>
 *     <li>Group G contains A</li>
 * </ul>
 * <br/>
 * WHEN:
 * <ul>
 *     <li>HostedRepository B is merged into HostedRepository A</li>
 *     <li>Metadata path P is requested from HostedRepository A <b>after promotion events have settled</b></li>
 * </ul>
 * <br/>
 * THEN:
 * <ul>
 *     <li>HostedRepository A's metadata path P should reflect values in HostedRepository B's metadata path P</li>
 *     <li>Group G's metadata path P should reflect values in A and B</li>
 * </ul>
 */
public class HostedMetadataRemergedOnPathPromoteSnapshotTest
        extends AbstractContentManagementTest
{
    private static final String HOSTED_A_NAME= "A";
    private static final String HOSTED_B_NAME= "B";

    private static final String GROUP_G_NAME= "G";

    private static final String A_VERSION = "1.0-SNAPSHOT";
    private static final String B_VERSION = "1.1-SNAPSHOT";

    private static final String PATH = "/org/foo/bar/maven-metadata.xml";

    private static final String POM_PATH_TEMPLATE = "/org/foo/bar/%version%/bar-%version%.pom";

    /* @formatter:off */
    private static final String REPO_CONTENT_TEMPLATE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<metadata>\n" +
        "  <groupId>org.foo</groupId>\n" +
        "  <artifactId>bar</artifactId>\n" +
        "  <versioning>\n" +
        "    <latest>%version%</latest>\n" +
        "    <versions>\n" +
        "      <version>%version%</version>\n" +
        "    </versions>\n" +
        "    <lastUpdated>20150722164334</lastUpdated>\n" +
        "  </versioning>\n" +
        "</metadata>\n";
    /* @formatter:on */

    private static final String POM_CONTENT_TEMPLATE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<project>\n" +
        "  <modelVersion>4.0.0</modelVersion>\n" +
        "  <groupId>org.foo</groupId>\n" +
        "  <artifactId>bar</artifactId>\n" +
        "  <version>%version%</version>\n" +
        "  <name>Bar</name>\n" +
        "  <dependencies>\n" +
        "    <dependency>\n" +
        "      <groupId>org.something</groupId>\n" +
        "      <artifactId>oh</artifactId>\n" +
        "      <version>1.0.1</version>\n" +
        "    </dependency>\n" +
        "  </dependencies>\n" +
        "</project>\n";
    /* @formatter:on */

    /* @formatter:off */
    private static final String AFTER_PROMOTE_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<metadata>\n" +
        "  <groupId>org.foo</groupId>\n" +
        "  <artifactId>bar</artifactId>\n" +
        "  <versioning>\n" +
        "    <latest>1.1-SNAPSHOT</latest>\n" +
        "    <versions>\n" +
        "      <version>1.0-SNAPSHOT</version>\n" +
        "      <version>1.1-SNAPSHOT</version>\n" +
        "    </versions>\n" +
        "    <lastUpdated>20150722164334</lastUpdated>\n" +
        "  </versioning>\n" +
        "</metadata>\n";
    /* @formatter:on */

    private HostedRepository a;
    private HostedRepository b;
    private Group g;

    private String aPreContent;
    private String bContent;

    private String aPomPath;
    private String aPomContent;

    private String bPomPath;
    private String bPomContent;

    private final IndyPromoteClientModule promote = new IndyPromoteClientModule();

    @Before
    public void setupRepos()
            throws IndyClientException
    {
        String message = "test setup";

        a = new HostedRepository( PKG_TYPE_MAVEN, HOSTED_A_NAME );
        a.setAllowSnapshots( true );
        a = client.stores().create( a, message, HostedRepository.class );

        b = new HostedRepository( PKG_TYPE_MAVEN, HOSTED_B_NAME );
        b.setAllowSnapshots( true );
        b = client.stores().create( b, message, HostedRepository.class );

        g = client.stores().create( new Group( PKG_TYPE_MAVEN, GROUP_G_NAME, a.getKey() ), message, Group.class );

        aPomPath = POM_PATH_TEMPLATE.replaceAll( "%version%", A_VERSION );
        aPomContent = POM_CONTENT_TEMPLATE.replaceAll( "%version%", A_VERSION );

        client.content()
              .store( a.getKey(), aPomPath, new ByteArrayInputStream(
                              aPomContent.getBytes() ) );

        aPreContent = REPO_CONTENT_TEMPLATE.replaceAll( "%version%", A_VERSION );

        client.content()
              .store( a.getKey(), PATH, new ByteArrayInputStream(
                              aPreContent.getBytes() ) );

        //
        bPomPath = POM_PATH_TEMPLATE.replaceAll( "%version%", B_VERSION );
        bPomContent = POM_CONTENT_TEMPLATE.replaceAll( "%version%", B_VERSION );

        client.content()
              .store( b.getKey(), bPomPath, new ByteArrayInputStream(
                              bPomContent.getBytes() ) );

        bContent = REPO_CONTENT_TEMPLATE.replaceAll( "%version%", B_VERSION );

        client.content()
              .store( b.getKey(), PATH, new ByteArrayInputStream(
                              bContent.getBytes() ) );
    }

    @Test
    @Category( EventDependent.class )
    public void run()
            throws Exception
    {
        // verify our initial state
        assertMetadataContent( a, PATH, aPreContent );

        PathsPromoteRequest request = new PathsPromoteRequest( b.getKey(), a.getKey(), new String[] { PATH, bPomPath } );

        // Pre-existing maven-metadata.xml should NOT cause a failure!
        request.setFailWhenExists( true );

        PathsPromoteResult response = promote.promoteByPath( request );

        assertThat( response.succeeded(), equalTo( true ) );

        waitForEventPropagation();

        // Group G's metadata path P should reflect values in A and B
        assertMetadataContent( g, PATH, AFTER_PROMOTE_CONTENT );

        // Promotion to repo A should trigger re-merge of maven-metadata.xml, adding the version from repo B to that in A.
        assertMetadataContent( a, PATH, AFTER_PROMOTE_CONTENT );

    }

    @Override
    protected Collection<IndyClientModule> getAdditionalClientModules()
    {
        return Collections.singleton( promote );
    }

    @Override
    protected boolean createStandardTestStructures()
    {
        return false;
    }

}
