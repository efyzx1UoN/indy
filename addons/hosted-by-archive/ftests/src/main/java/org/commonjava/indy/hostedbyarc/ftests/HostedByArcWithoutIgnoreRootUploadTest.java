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
package org.commonjava.indy.hostedbyarc.ftests;

import org.commonjava.indy.hostedbyarc.client.IndyHostedByArchiveClientModule;
import org.commonjava.indy.model.core.HostedRepository;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * <b>GIVEN:</b>
 * <ul>
 *     <li>A zip contains valid maven structured files</li>
 *     <li>Valid paths in zip file is starting from root with no prefix path</li>
 * </ul>
 *
 * <br/>
 * <b>WHEN:</b>
 * <ul>
 *     <li>Upload this zip file to indy for creating hosted</li>
 * </ul>
 *
 * <br/>
 * <b>THEN:</b>
 * <ul>
 *     <li>The hosted is created correctly</li>
 *     <li>The content in hosted can fetch correctly with valid path</li>
 * </ul>
 */
public class HostedByArcWithoutIgnoreRootUploadTest
        extends AbstractHostedByArcTest
{

    @Test
    public void testUploadZipAndCreate() throws Exception{
        IndyHostedByArchiveClientModule module = client.module( IndyHostedByArchiveClientModule.class );

        final String hostedRepoName = "hosted-zip-no-ignore";

        HostedRepository repo = module.createRepo( getZipFile(), hostedRepoName );

        assertThat( repo, notNullValue() );
        assertThat( repo.getName(), equalTo( hostedRepoName ) );

        boolean exists = client.content().exists( repo.getKey(), "org/foo/bar/1.0/foo-bar-1.0.pom" );

        assertTrue(exists);
    }

    @Override
    protected String getZipFileResource()
    {
        return "repo-without-ignore.zip";
    }

    @Override
    protected boolean enabled()
    {
        return true;
    }
}
