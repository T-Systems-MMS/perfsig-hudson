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
import de.tsystems.mms.apm.performancesignature.util.DTPerfSigUtils;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.junit.TestDataPublisher;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by rapi on 27.05.2015.
 */
public class DTPerfSigTestDataPublisher extends TestDataPublisher {
    //ToDo parameter needed?
    private final String testCase;

    @DataBoundConstructor
    public DTPerfSigTestDataPublisher(final String testCase) {
        this.testCase = testCase;
    }

    @Override
    public TestResultAction.Data getTestData(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, TestResult testResult) throws IOException, InterruptedException {
        final PrintStream logger = listener.getLogger();
        final DTPerfSigRecorder dtRecorder = DTPerfSigUtils.getRecorder(build);

        if (dtRecorder == null) {
            logger.println("Unable to find Dynatrace Configuration Post Build Step!");
            return null;
        }

        final DTServerConnection connection = new DTServerConnection(dtRecorder.getProtocol(), dtRecorder.getHost(), dtRecorder.getPort(),
                dtRecorder.getUsername(), dtRecorder.getPassword(), dtRecorder.isVerifyCertificate(), dtRecorder.isUseJenkinsProxy(), dtRecorder.getCustomProxy());

        logger.println(Messages.DTPerfSigRecorder_VerifyDTConnection());
        if (!connection.validateConnection()) {
            logger.println(Messages.DTPerfSigRecorder_DTConnectionError());
            checkForUnstableResult(build);
        }

        final List<TestRun> testRuns = new ArrayList<TestRun>();
        final List<DTPerfSigRegisterEnvVars> envVars = build.getActions(DTPerfSigRegisterEnvVars.class);
        for (DTPerfSigRegisterEnvVars registerEnvVars : envVars) {
            if (StringUtils.isNotBlank(registerEnvVars.getTestRunID())) {
                try {
                    TestRun testRun = connection.getTestRunFromXML(dtRecorder.getProfile(), registerEnvVars.getTestRunID());
                    if (testRun == null || testRun.getTestResults() == null || testRun.getTestResults().isEmpty()) {
                        logger.println(Messages.DTPerfSigRecorder_XMLReportError());
                        if (dtRecorder.isModifyBuildResult()) build.setResult(Result.FAILURE);
                    } else {
                        testRuns.add(testRun);
                        logger.println(String.format(Messages.DTPerfSigRecorder_XMLReportResults(), testRun.getTestResults().size(), " " + testRun.getTestRunID()));
                    }
                } catch(Exception e) {
                    logger.println(e);
                    if (!dtRecorder.isModifyBuildResult()) return null;
                }
            }
        }

        DTPerfSigTestData perfSigTestData = new DTPerfSigTestData(build, testRuns);
        build.addAction(new DTPerfSigTestDataWrapper(perfSigTestData));
        return perfSigTestData;
    }

    private void checkForUnstableResult(final Run run) {
        DTPerfSigRecorder recorder = DTPerfSigUtils.getRecorder((AbstractBuild) run);
        if (getBuildResult(run).isBetterOrEqualTo(Result.UNSTABLE) && recorder.isModifyBuildResult()) {
            run.setResult(Result.FAILURE);
        }
    }

    private Result getBuildResult(final Run run) {
        Result result = run.getResult();
        if (result == null) {
            throw new IllegalStateException("run is ongoing");
        }
        return result;
    }

    @Extension
    public static final class DTPerfSigTestDataPublisherDescriptor extends Descriptor<TestDataPublisher> {
        public DTPerfSigTestDataPublisherDescriptor() {
            load();
        }

        @Override
        public String getDisplayName() {
            return "Add Dynatrace Performance Data to each test result";
        }
    }

}
