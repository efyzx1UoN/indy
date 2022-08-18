/**
 * Copyright (C) 2011-2020 Red Hat, Inc. (https://github.com/Commonjava/indy)
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
package org.commonjava.indy.folo.ftest.report;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.commonjava.indy.client.core.util.UrlUtils;
import org.commonjava.indy.folo.client.IndyFoloAdminClientModule;
import org.commonjava.indy.folo.client.IndyFoloContentClientModule;
import org.commonjava.indy.folo.dto.TrackedContentDTO;
import org.commonjava.indy.folo.dto.TrackedContentEntryDTO;
import org.commonjava.indy.ftest.core.category.EventDependent;
import org.commonjava.indy.model.core.RemoteRepository;
import org.commonjava.indy.model.core.StoreKey;
import org.commonjava.indy.test.fixture.core.CoreServerFixture;
import org.commonjava.test.http.expect.ExpectationServer;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static org.commonjava.indy.model.core.StoreType.remote;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@Ignore
@Category( EventDependent.class )
public class RetrieveTrackingReportAfterCacheExpirationTest
    extends AbstractTrackingReportTest
{

    @Rule
    public ExpectationServer server = new ExpectationServer();

    @Override
    protected void initTestConfig( CoreServerFixture fixture )
            throws IOException
    {
        // TODO: This doesn't work this way any more!
        writeConfigFile( "conf.d/folo.conf", "[folo]\ncache.timeout.seconds=1" );
    }

    @Test
    public void run()
        throws Exception
    {
        final String trackingId = newName();
        final String repoId = "repo";
        final String path = "/path/to/foo.class";

        final InputStream stream = new ByteArrayInputStream( ( "This is a test: " + System.nanoTime() ).getBytes() );

        server.expect( server.formatUrl( repoId, path ), 200, stream );

        RemoteRepository rr = new RemoteRepository( repoId, server.formatUrl( repoId ) );
        rr = client.stores()
                   .create( rr, "adding test remote", RemoteRepository.class );

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final InputStream in = client.module( IndyFoloContentClientModule.class )
                                     .get( trackingId, remote, repoId, path );

        IOUtils.copy( in, baos );
        in.close();

        final byte[] bytes = baos.toByteArray();

        final String md5 = md5Hex( bytes );
        final String sha256 = sha256Hex( bytes );

        assertThat( md5, equalTo( DigestUtils.md5Hex( bytes ) ) );
        assertThat( sha256, equalTo( DigestUtils.sha256Hex( bytes ) ) );

        // sleep so the cache evicts the record
        Thread.sleep( 2000 );

        assertThat( client.module( IndyFoloAdminClientModule.class ).sealTrackingRecord( trackingId ),
                    equalTo( true ) );

        final TrackedContentDTO report = client.module( IndyFoloAdminClientModule.class )
                                               .getTrackingReport( trackingId );
        assertThat( report, notNullValue() );

        final Set<TrackedContentEntryDTO> downloads = report.getDownloads();

        assertThat( downloads, notNullValue() );
        assertThat( downloads.size(), equalTo( 1 ) );

        final TrackedContentEntryDTO entry = downloads.iterator()
                                                    .next();

        System.out.println( entry );

        assertThat( entry, notNullValue() );
        assertThat( entry.getStoreKey(), equalTo( new StoreKey( remote, repoId ) ) );
        assertThat( entry.getPath(), equalTo( path ) );
        assertThat( entry.getLocalUrl(),
                    equalTo( client.content().contentUrl( remote, repoId, path ) ) );
        assertThat( entry.getOriginUrl(), equalTo( server.formatUrl( repoId, path ) ) );
        assertThat( entry.getMd5(), equalTo( md5 ) );
        assertThat( entry.getSha256(), equalTo( sha256 ) );
    }

    @Override
    protected boolean createStandardStores()
    {
        return false;
    }
}
