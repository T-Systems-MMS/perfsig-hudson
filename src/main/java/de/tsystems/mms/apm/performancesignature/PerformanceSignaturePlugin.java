/*
 * Copyright (c) 2014 T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.tsystems.mms.apm.performancesignature;

import de.tsystems.mms.apm.performancesignature.util.PerfSigUtils;
import hudson.FilePath;
import hudson.Plugin;
import hudson.init.Initializer;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.util.VersionNumber;
import org.apache.commons.io.filefilter.RegexFileFilter;

import java.io.IOException;

import static hudson.init.InitMilestone.JOB_LOADED;

/**
 * Created by rapi on 01.12.2015.
 */
public class PerformanceSignaturePlugin extends Plugin {
    @Initializer(after = JOB_LOADED)
    public static void init1() throws IOException, InterruptedException {
        // Check for old dashboard configurations
        Hudson jenkins = PerfSigUtils.getInstanceOrDie();
        if (jenkins.getPluginManager()
                .getPlugin("performance-signature").getVersionNumber().isOlderThan(new VersionNumber("1.6.0"))) {
            for (AbstractProject<?, ?> job : jenkins.getAllItems(AbstractProject.class)) {
                FilePath jobPath = new FilePath(job.getConfigFile().getFile()).getParent();
                if (jobPath == null) continue;
                for (FilePath file : jobPath.list(new RegexFileFilter(".*-config.json"))) {
                    file.delete();
                }
            }
        }
    }
}
