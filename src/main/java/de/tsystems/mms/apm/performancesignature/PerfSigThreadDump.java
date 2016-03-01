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

import de.tsystems.mms.apm.performancesignature.dynatrace.model.Agent;
import de.tsystems.mms.apm.performancesignature.dynatrace.rest.DTServerConnection;
import de.tsystems.mms.apm.performancesignature.dynatrace.rest.RESTErrorException;
import de.tsystems.mms.apm.performancesignature.model.CredProfilePair;
import de.tsystems.mms.apm.performancesignature.model.DynatraceServerConfiguration;
import de.tsystems.mms.apm.performancesignature.util.PerfSigUtils;
import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

public class PerfSigThreadDump extends Builder {
    private static final int waitForDumpTimeout = 60000;
    private static final int waitForDumpPollingInterval = 5000;
    private final String dynatraceProfile, agent, host;
    private boolean lockSession;

    @DataBoundConstructor
    public PerfSigThreadDump(final String dynatraceProfile, final String agent, final String host, final boolean lockSession) {
        this.dynatraceProfile = dynatraceProfile;
        this.agent = agent;
        this.host = host;
        this.lockSession = lockSession;
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
        PrintStream logger = listener.getLogger();

        DynatraceServerConfiguration serverConfiguration = PerfSigUtils.getServerConfiguration(dynatraceProfile);
        if (serverConfiguration == null)
            throw new AbortException("failed to lookup Dynatrace server configuration");

        CredProfilePair pair = serverConfiguration.getCredProfilePair(dynatraceProfile);
        if (pair == null)
            throw new AbortException("failed to lookup Dynatrace server profile");

        final DTServerConnection connection = new DTServerConnection(serverConfiguration, pair);
        List<Agent> agents = connection.getAgents();

        for (Agent agent : agents) {
            if (agent.getName().equals(this.agent) && agent.getSystemProfile().equals(pair.getProfile()) && agent.getHost().equals(this.host)) {
                logger.println("Creating Memory Dump for " + agent.getSystemProfile() + "-" + agent.getName() + "-" + agent.getHost() + "-" + agent.getProcessId());

                String threadDump = connection.threadDump(agent.getName(), agent.getHost(), agent.getProcessId(), getLockSession());
                if (StringUtils.isBlank(threadDump))
                    throw new RESTErrorException("Thread Dump wasnt taken");
                int timeout = waitForDumpTimeout;
                boolean dumpFinished = connection.threadDumpStatus(threadDump).isResultValueTrue();
                while ((!dumpFinished) && (timeout > 0)) {
                    Thread.sleep(waitForDumpPollingInterval);
                    timeout -= waitForDumpPollingInterval;
                    dumpFinished = connection.threadDumpStatus(threadDump).isResultValueTrue();
                }
                if (dumpFinished) {
                    logger.println(Messages.PerfSigThreadDump_SuccessfullyCreatedThreadDump() + agent.getName());
                    return true;
                } else {
                    throw new RESTErrorException("Timeout is raised");
                }
            }
        }
        throw new AbortException(String.format(Messages.PerfSigThreadDump_AgentNotConnected(), agent));
    }

    public String getDynatraceProfile() {
        return dynatraceProfile;
    }

    public String getAgent() {
        return agent;
    }

    public String getHost() {
        return host;
    }

    public boolean getLockSession() {
        return lockSession;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public static final boolean defaultLockSession = false;

        public FormValidation doCheckAgent(@QueryParameter final String agent) {
            FormValidation validationResult;
            if (StringUtils.isNotBlank(agent)) {
                validationResult = FormValidation.ok();
            } else {
                validationResult = FormValidation.error(Messages.PerfSigThreadDump_AgentNotValid());
            }
            return validationResult;
        }

        public FormValidation doCheckHost(@QueryParameter final String host) {
            FormValidation validationResult;
            if (StringUtils.isNotBlank(host)) {
                validationResult = FormValidation.ok();
            } else {
                validationResult = FormValidation.error(Messages.PerfSigThreadDump_AgentNotValid());
            }
            return validationResult;
        }

        public ListBoxModel doFillDynatraceProfileItems() {
            return PerfSigUtils.listToListBoxModel(PerfSigUtils.getDTConfigurations());
        }

        public ListBoxModel doFillAgentItems(@QueryParameter final String dynatraceProfile) {
            DynatraceServerConfiguration serverConfiguration = PerfSigUtils.getServerConfiguration(dynatraceProfile);
            if (serverConfiguration != null) {
                CredProfilePair pair = serverConfiguration.getCredProfilePair(dynatraceProfile);
                if (pair != null) {
                    DTServerConnection connection = new DTServerConnection(serverConfiguration, pair);
                    return PerfSigUtils.listToListBoxModel(connection.getAgents());
                }
            }
            return null;
        }

        public ListBoxModel doFillHostItems(@QueryParameter final String dynatraceProfile, @QueryParameter final String agent) {
            DynatraceServerConfiguration serverConfiguration = PerfSigUtils.getServerConfiguration(dynatraceProfile);
            if (serverConfiguration != null) {
                CredProfilePair pair = serverConfiguration.getCredProfilePair(dynatraceProfile);
                if (pair != null) {
                    DTServerConnection connection = new DTServerConnection(serverConfiguration, pair);
                    List<Agent> agents = connection.getAgents();
                    ListBoxModel hosts = new ListBoxModel();
                    for (Agent a : agents)
                        if (a.getName().equals(agent))
                            hosts.add(a.getHost());
                    return hosts;
                }
            }
            return null;
        }

        public boolean isApplicable(final Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return Messages.PerfSigThreadDump_DisplayName();
        }
    }
}
