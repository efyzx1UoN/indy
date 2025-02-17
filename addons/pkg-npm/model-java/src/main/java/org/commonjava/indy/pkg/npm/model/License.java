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
package org.commonjava.indy.pkg.npm.model;

import io.swagger.annotations.ApiModel;

@ApiModel( description = "These styles are now deprecated. Instead, use SPDX expressions." )
public class License
{
    private final String type;

    private final String url;

    protected License()
    {
        this.type = null;
        this.url = null;
    }

    public License( final String type )
    {
        this.type = type;
        this.url = null;
    }

    public License( final String type, final String url )
    {
        this.type = type;
        this.url = url;
    }

    public String getType()
    {
        return type;
    }

    public String getUrl()
    {
        return url;
    }
}
