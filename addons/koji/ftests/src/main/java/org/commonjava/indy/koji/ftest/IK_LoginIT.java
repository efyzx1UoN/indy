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
package org.commonjava.indy.koji.ftest;

import org.junit.Test;

/**
 * This IT tests that Indy can boot normally with Koji support enabled. There are no assertions, because failure to
 * boot will cause a failure of this test in the setup phase.
 *
 * Created by jdcasey on 5/26/16.
 */
public class IK_LoginIT
    extends AbstractKojiIT
{
    @Test
    public void run()
    {
        // nop
    }
}
