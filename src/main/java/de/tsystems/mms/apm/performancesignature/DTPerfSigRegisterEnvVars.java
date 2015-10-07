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

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentContributingAction;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by rapi on 12.04.2015.
 */
public class DTPerfSigRegisterEnvVars implements EnvironmentContributingAction {
    public static final String TESTRUN_ID_KEY = "DYNATRACE_TESTRUN_ID";
    public static final String SESSIONCOUNT = "DYNATRACE_SESSIONCOUNT";

    private final String sessionName, testRunID, testCase;

    public DTPerfSigRegisterEnvVars(final String sessionName, final String testCase, final String testRunID) {
        this.sessionName = sessionName;
        this.testCase = testCase;
        this.testRunID = testRunID;
    }

    @Override
    public void buildEnvVars(final AbstractBuild<?, ?> abstractBuild, final EnvVars envVars) {
        if (StringUtils.isNotBlank(testRunID)) {
            byte sessionCount = Byte.valueOf(envVars.get(SESSIONCOUNT) == null ? "0" : envVars.get(SESSIONCOUNT));
            sessionCount++;

            envVars.put(TESTRUN_ID_KEY + sessionCount, testRunID);
            envVars.put(SESSIONCOUNT, String.valueOf(sessionCount));
        }
    }

    public String getSessionName() {
        return sessionName;
    }

    public String getTestRunID() {
        return testRunID;
    }

    public String getTestCase() {
        return testCase;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "put TestRunID and SessionCount in EnvVars";
    }

    @Override
    public String getUrlName() {
        return null;
    }
}
