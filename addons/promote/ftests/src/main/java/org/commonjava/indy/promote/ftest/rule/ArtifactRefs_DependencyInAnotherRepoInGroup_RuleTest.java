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
package org.commonjava.indy.promote.ftest.rule;

import org.apache.commons.io.IOUtils;
import org.commonjava.indy.ftest.core.category.EventDependent;
import org.commonjava.indy.model.core.Group;
import org.commonjava.indy.model.core.HostedRepository;
import org.commonjava.indy.promote.model.GroupPromoteRequest;
import org.commonjava.indy.promote.model.GroupPromoteResult;
import org.commonjava.indy.promote.model.ValidationResult;
import org.commonjava.indy.promote.model.ValidationRuleSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

/**
 * Created by jdcasey on 9/23/15.
 */
public class ArtifactRefs_DependencyInAnotherRepoInGroup_RuleTest
        extends AbstractValidationRuleTest<Group>
{

    private static final String RULE = "maven-artifact-refs-via.groovy";

    private static final String PREFIX = "artifact-refs-via/";

    private HostedRepository otherSource;

    private Group other;

    @Test
    @Category( EventDependent.class )
    public void run()
            throws Exception
    {
        String invalid = "org/foo/invalid/1/invalid-1.pom";
        String valid = "org/foo/valid/1.1/valid-1.1.pom";

        String validDepPom = "org/bar/dep/1.0/dep-1.0.pom";
        String validDepJar = "org/bar/dep/1.0/dep-1.0.jar";
        String content = "this is some content";

        client.content().store( otherSource.getKey(), validDepPom, new ByteArrayInputStream( content.getBytes() ) );
        client.content().store( otherSource.getKey(), validDepJar, new ByteArrayInputStream( content.getBytes() ) );

        InputStream stream = client.content().get( other.getKey(), validDepPom );
        String retrieved = IOUtils.toString( stream );
        stream.close();

        assertThat( validDepPom + " invalid from: " + other.getKey(), retrieved, equalTo( content ) );

        stream = client.content().get( other.getKey(), validDepJar );
        retrieved = IOUtils.toString( stream );
        stream.close();

        assertThat( validDepJar + " invalid from: " + other.getKey(), retrieved, equalTo( content ) );

        deployResource( invalid, PREFIX + "invalid-external-dep.pom.xml");
        deployResource( valid, PREFIX + "valid-single-external-dep.pom.xml" );

        waitForEventPropagation();

        GroupPromoteRequest request = new GroupPromoteRequest( source.getKey(), target.getName() );
        GroupPromoteResult result = module.promoteToGroup( request );
        assertThat( result, notNullValue() );

        ValidationResult validations = result.getValidations();
        assertThat( validations, notNullValue() );

        Map<String, String> validatorErrors = validations.getValidatorErrors();
        assertThat( validatorErrors, notNullValue() );

        System.out.println(validatorErrors);

        String errors = validatorErrors.get( RULE );
        assertThat( errors, notNullValue() );

        System.out.println(validatorErrors);
        assertThat( errors.contains( valid ), equalTo( false ) );
        assertThat( errors.contains( invalid ), equalTo( true ) );
    }

    public ArtifactRefs_DependencyInAnotherRepoInGroup_RuleTest()
    {
        super( Group.class );
    }

    @Override
    protected String getRuleScriptFile()
    {
        return RULE;
    }

    @Override
    protected String getRuleScriptContent()
            throws IOException
    {
        String path = "promote/rules/" + RULE;
        return readTestResource( path );
    }

    @Override
    protected ValidationRuleSet getRuleSet()
    {
        ValidationRuleSet ruleSet = new ValidationRuleSet();
        ruleSet.setName( "test" );
        ruleSet.setStoreKeyPattern( "group:target" );
        ruleSet.setRuleNames( Collections.singletonList( getRuleScriptFile() ) );

        ruleSet.setValidationParameters( Collections.singletonMap( "availableInStores", "group:other" ) );

        return ruleSet;
    }

    @Override
    public void start()
            throws Throwable
    {
        super.start();

        otherSource = new HostedRepository( "otherSource" );
        otherSource = client.stores().create( otherSource, "Creating secondary content source", HostedRepository.class );

        other = new Group( "other", source.getKey(), otherSource.getKey() );
        other = client.stores().create( other, "Creating secondary content group", Group.class );

        Logger logger = LoggerFactory.getLogger( getClass() );
        logger.info( "{} contains members: {}", other, other.getConstituents() );
    }
}
