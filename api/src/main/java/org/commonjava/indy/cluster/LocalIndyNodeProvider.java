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
package org.commonjava.indy.cluster;

import org.commonjava.indy.conf.IndyConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class LocalIndyNodeProvider
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    private IndyConfiguration indyConfig;

    private IndyNode localNode;

    @PostConstruct
    public void setup()
    {
        localNode = new IndyNode( indyConfig.getNodeId() );
    }

    public IndyNode getLocalIndyNode()
    {
        return localNode;
    }
}
