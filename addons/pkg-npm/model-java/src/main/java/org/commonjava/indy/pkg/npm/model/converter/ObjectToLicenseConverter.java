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
package org.commonjava.indy.pkg.npm.model.converter;

import com.fasterxml.jackson.databind.util.StdConverter;
import org.commonjava.indy.pkg.npm.model.License;

import java.util.List;

public class ObjectToLicenseConverter extends StdConverter<Object, License> {

    @Override
    public License convert(Object o) {
        if (o instanceof List)
        {
            // Use SPDX expressions, ref https://docs.npmjs.com/cli/v7/configuring-npm/package-json
            // e.g, parse "[MIT, Apache2]" to "(MIT OR Apache2)"
            String license = o.toString().replaceAll("\\[|\\]", "");
            return new License("(" + license.replaceAll(",", " OR") + ")");
        }
        return new License(o.toString());
    }
}
