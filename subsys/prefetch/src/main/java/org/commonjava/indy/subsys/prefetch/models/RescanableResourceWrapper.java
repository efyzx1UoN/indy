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
package org.commonjava.indy.subsys.prefetch.models;

import org.commonjava.maven.galley.model.ConcreteResource;

public class RescanableResourceWrapper
{
    private ConcreteResource resource;

    private Boolean rescan;

    public RescanableResourceWrapper( ConcreteResource resource, Boolean rescan )
    {
        this.resource = resource;
        this.rescan = rescan;
    }

    public ConcreteResource getResource()
    {
        return resource;
    }

    public Boolean isRescan()
    {
        return rescan;
    }

    @Override
    public int hashCode()
    {
        return resource.hashCode();
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( !( obj instanceof RescanableResourceWrapper ) )
        {
            return false;
        }
        RescanableResourceWrapper that = (RescanableResourceWrapper) obj;
        if ( this.resource == null && that.resource == null )
        {
            return true;
        }
        else if ( this.resource != null && that.resource != null )
        {
            return this.resource.equals( that.resource );
        }
        else
        {
            return false;
        }
    }

    @Override
    public String toString()
    {
        return this.resource.toString() + ", rescan=" + rescan;
    }
}
