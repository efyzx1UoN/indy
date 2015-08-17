/**
 * Copyright (C) 2011 Red Hat, Inc. (jdcasey@commonjava.org)
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
package org.commonjava.aprox.depgraph.rest;

import java.util.Set;

import javax.inject.Inject;

import org.commonjava.aprox.AproxWorkflowException;
import org.commonjava.aprox.depgraph.model.WorkspaceList;
import org.commonjava.maven.atlas.graph.RelationshipGraphException;
import org.commonjava.maven.atlas.graph.RelationshipGraphFactory;

public class WorkspaceController
{

    @Inject
    private RelationshipGraphFactory graphFactory;

    public void delete( final String id )
                    throws AproxWorkflowException
    {
        try
        {
            if ( !graphFactory.deleteWorkspace( id ) )
            {
                throw new AproxWorkflowException( "Delete failed for workspace: {}", id );
            }
        }
        catch ( final RelationshipGraphException e )
        {
            throw new AproxWorkflowException( "Error deleting workspace: {}. Reason: {}", e, id, e.getMessage() );
        }
    }

    public WorkspaceList list()
                    throws AproxWorkflowException
    {
        Set<String> ids = graphFactory.listWorkspaces();
        return new WorkspaceList( ids );
    }

}
