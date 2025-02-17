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
package org.commonjava.indy.implrepo.skim;

import org.apache.commons.io.IOUtils;
import org.commonjava.indy.model.core.Group;
import org.commonjava.indy.model.core.StoreKey;
import org.commonjava.indy.model.core.StoreType;
import org.commonjava.indy.pkg.maven.model.MavenPackageTypeDescriptor;
import org.commonjava.indy.test.fixture.core.CoreServerFixture;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * <b>GIVEN:</b>
 * <ul>
 *     <li>Implied config enabled group public but not enabled build_disabled</li>
 *     <li>Both group contains remote repo test, which contains a pom with path p and refer a implied repo i</li>
 * </ul>
 *
 * <br/>
 * <b>WHEN:</b>
 * <ul>
 *     <li>Access pom through path p in group pub</li>
 * </ul>
 *
 * <br/>
 * <b>THEN:</b>
 * <ul>
 *     <li>A new implied remote repo point to i will be added to group public</li>
 *     <li>This new implied repo will NOT be added to group build_enabled</li>
 * </ul>
 */
public class DisabledGroupNotAddingImpliedTest
        extends AbstractSkimFunctionalTest
{
    private static final String REPO = "i-repo-one";

    private static final String DISABLED_GROUP = "builds-disabled";

    @Test
    public void skimPomForRepoAndAddItInGroup()
            throws Exception
    {
        logger.debug( "Start testing!" );
        final PomRef ref = loadPom( "one-repo", Collections.singletonMap( "one-repo.url", server.formatUrl( REPO ) ) );

        server.expect( "HEAD", server.formatUrl( REPO, "/" ), 200, (String) null  );
        server.expect( server.formatUrl( TEST_REPO, ref.path ), 200, ref.pom );

        final StoreKey testRepoKey =
                new StoreKey( MavenPackageTypeDescriptor.MAVEN_PKG_KEY, StoreType.remote, TEST_REPO );

        final StoreKey impliedRepoKey = new StoreKey( MavenPackageTypeDescriptor.MAVEN_PKG_KEY, StoreType.remote, REPO );

        Group disabledGroup = new Group( MavenPackageTypeDescriptor.MAVEN_PKG_KEY, DISABLED_GROUP );
        disabledGroup.addConstituent( testRepoKey );
        disabledGroup = client.stores()
                              .create( disabledGroup, String.format( "Create group %s", DISABLED_GROUP ),
                                       Group.class );

        assertThat( "Disabled repo should not contain implied before getting pom",
                    disabledGroup.getConstituents().contains( impliedRepoKey ), equalTo( false ) );

        logger.debug( "Start fetching pom!" );
        final StoreKey pubGroupKey = new StoreKey( MavenPackageTypeDescriptor.MAVEN_PKG_KEY, StoreType.group,
                                                   PUBLIC );
        final InputStream stream = client.content().get( pubGroupKey, ref.path );
        final String downloaded = IOUtils.toString( stream );
        IOUtils.closeQuietly( stream );

        assertThat( "SANITY: downloaded POM is wrong!", downloaded, equalTo( ref.pom ) );

        // sleep while event observer runs...
        System.out.println( "Waiting 5s for events to run." );
        Thread.sleep( 5000 );

        final Group pub = client.stores().load( pubGroupKey, Group.class );
        assertThat( "Group public does not contain implied repository",
                    pub.getConstituents().contains( impliedRepoKey ), equalTo( true ) );

        disabledGroup = client.stores().load( disabledGroup.getKey(), Group.class );
        assertThat( "Disabled group contains implied repository, but it is disabled in implied config",
                    disabledGroup.getConstituents().contains( impliedRepoKey ), equalTo( false ) );

    }

    @Override
    protected void initTestConfig( CoreServerFixture fixture )
            throws IOException
    {
        writeConfigFile( "conf.d/implied-repos.conf",
                         String.format( "[implied-repos]\nenabled=true\nenabled.group=%s\nenabled.group=build_.+",
                                        PUBLIC ) );
    }

}
