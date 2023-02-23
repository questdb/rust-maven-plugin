/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2023 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.maven.rust;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * An example of a Maven plugin.
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE, threadSafe = true)
public class CargoBuildMojo extends CargoMojoBase {
    /**
     * Location to copy the built Rust binaries to.
     * If unset, the binaries are not copied and remain in the target directory.
     * <p>
     * See also `copyWithPlatformDir`.
     */
    @Parameter(property = "copyTo")
    private String copyTo;

    /**
     * Further nest copy into a subdirectory named through the following expression:
     * (System.getProperty("os.name") + "_" + System.getProperty("os.arch")).toLowerCase();
     * <p>
     * See also `copyTo`.
     */
    @Parameter(property = "copyWithPlatformDir")
    private boolean copyWithPlatformDir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final Crate crate = new Crate(
                getCrateRoot(),
                getTargetRootDir(),
                extractCrateParams());
        crate.setLog(getLog());
        crate.build();
        crate.copyArtifacts();
    }

    private Crate.Params extractCrateParams() {
        final Crate.Params params = getCommonCrateParams();
        if (copyTo != null) {
            Path copyToDir = Paths.get(copyTo);
            if (!copyToDir.isAbsolute()) {
                copyToDir = project.getBasedir().toPath()
                        .resolve(copyToDir);
            }
            params.copyToDir = copyToDir;
        }
        params.copyWithPlatformDir = copyWithPlatformDir;
        return params;
    }
}
