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

import de.tsystems.mms.apm.performancesignature.dynatrace.model.TestRun;
import de.tsystems.mms.apm.performancesignature.dynatrace.rest.DTServerConnection;
import de.tsystems.mms.apm.performancesignature.dynatrace.rest.RESTErrorException;
import de.tsystems.mms.apm.performancesignature.model.CredProfilePair;
import de.tsystems.mms.apm.performancesignature.model.DynatraceServerConfiguration;
import de.tsystems.mms.apm.performancesignature.model.PerfSigTestData;
import de.tsystems.mms.apm.performancesignature.util.PerfSigUtils;
import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.junit.TestDataPublisher;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class PerfSigTestDataPublisher extends TestDataPublisher {
    private final String dynatraceProfile;

    @DataBoundConstructor
    public PerfSigTestDataPublisher(final String dynatraceProfile) {
        this.dynatraceProfile = dynatraceProfile;
    }

    public String getDynatraceProfile() {
        return dynatraceProfile;
    }

    @Override
    public TestResultAction.Data getTestData(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, TestResult testResult) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();

        DynatraceServerConfiguration serverConfiguration = PerfSigUtils.getServerConfiguration(dynatraceProfile);
        if (serverConfiguration == null)
            throw new AbortException("failed to lookup Dynatrace server configuration");

        CredProfilePair pair = serverConfiguration.getCredProfilePair(dynatraceProfile);
        if (pair == null)
            throw new AbortException("failed to lookup Dynatrace server profile");

        final DTServerConnection connection = new DTServerConnection(serverConfiguration, pair);

        logger.println(Messages.PerfSigRecorder_VerifyDTConnection());
        if (!connection.validateConnection()) {
            throw new RESTErrorException(Messages.PerfSigRecorder_DTConnectionError());
        }

        final List<TestRun> testRuns = new ArrayList<TestRun>();
        final List<PerfSigEnvInvisAction> envVars = build.getActions(PerfSigEnvInvisAction.class);
        for (PerfSigEnvInvisAction registerEnvVars : envVars) {
            if (StringUtils.isNotBlank(registerEnvVars.getTestRunID())) {
                TestRun testRun = connection.getTestRunFromXML(registerEnvVars.getTestRunID());
                if (testRun == null || testRun.getTestResults() == null || testRun.getTestResults().isEmpty()) {
                    throw new RESTErrorException(Messages.PerfSigRecorder_XMLReportError());
                } else {
                    testRuns.add(testRun);
                    logger.println(String.format(Messages.PerfSigRecorder_XMLReportResults(), testRun.getTestResults().size(), " " + testRun.getTestRunID()));
                }
            }
        }

        PerfSigTestData perfSigTestData = new PerfSigTestData(build, testRuns);
        build.addAction(new PerfSigTestDataWrapper(perfSigTestData));
        return perfSigTestData;
    }

    @Extension
    public static final class PerfSigTestDataPublisherDescriptor extends Descriptor<TestDataPublisher> {

        public ListBoxModel doFillDynatraceProfileItems() {
            return PerfSigUtils.listToListBoxModel(PerfSigUtils.getDTConfigurations());
        }

        @Override
        public String getDisplayName() {
            return "Add Dynatrace Performance Data to each test result";
        }
    }

}
