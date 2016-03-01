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

import de.tsystems.mms.apm.performancesignature.dynatrace.model.*;
import de.tsystems.mms.apm.performancesignature.dynatrace.rest.DTServerConnection;
import de.tsystems.mms.apm.performancesignature.dynatrace.rest.RESTErrorException;
import de.tsystems.mms.apm.performancesignature.model.*;
import de.tsystems.mms.apm.performancesignature.model.ConfigurationTestCase.ConfigurationTestCaseDescriptor;
import de.tsystems.mms.apm.performancesignature.util.PerfSigUtils;
import hudson.AbortException;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PerfSigRecorder extends Recorder {
    private final String dynatraceProfile;
    private final List<ConfigurationTestCase> configurationTestCases;
    private boolean exportSessions;
    private int nonFunctionalFailure;
    private transient List<String> availableSessions;

    @DataBoundConstructor
    public PerfSigRecorder(final String dynatraceProfile, final boolean exportSessions,
                           final List<ConfigurationTestCase> configurationTestCases, final JSONObject nonFunctionalFailure) {
        this.dynatraceProfile = dynatraceProfile;
        this.configurationTestCases = configurationTestCases;
        this.nonFunctionalFailure = nonFunctionalFailure.getInt("value");
        this.exportSessions = exportSessions;
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

        if (configurationTestCases == null) {
            throw new AbortException(Messages.PerfSigRecorder_MissingTestCases());
        }

        DTServerConnection connection = new DTServerConnection(serverConfiguration, pair);
        logger.println(Messages.PerfSigRecorder_VerifyDTConnection());
        if (!connection.validateConnection()) {
            throw new RESTErrorException(Messages.PerfSigRecorder_DTConnectionError());
        }

        if (serverConfiguration.getDelay() != 0) {
            logger.println(Messages.PerfSigRecorder_SleepingDelay() + " " + serverConfiguration.getDelay() + " sec");
            Thread.sleep(serverConfiguration.getDelay() * 1000);
        }
        logger.println(Messages.PerfSigRecorder_ReportDirectory() + " " + PerfSigUtils.getReportDirectory(build));

        for (BaseConfiguration profile : connection.getSystemProfiles()) {
            SystemProfile systemProfile = (SystemProfile) profile;
            if (pair.getProfile().equals(systemProfile.getId()) && systemProfile.isRecording()) {
                logger.println("Sesssion is still recording, trying to stop recording");
                PerfSigStopRecording stopRecording = new PerfSigStopRecording(dynatraceProfile, false);
                stopRecording.perform(build, launcher, listener);
                break;
            }
        }

        String sessionName, comparisonSessionName = null, singleFilename, comparisonFilename;
        int comparisonBuildNumber = 0;
        final int buildNumber = build.getNumber();
        final List<DashboardReport> dashboardReports = new ArrayList<DashboardReport>();

        Run<?, ?> previousRun = build.getPreviousNotFailedBuild();
        if (previousRun != null) {
            Run<?, ?> previousCompletedRun = build.getPreviousCompletedBuild();
            if (previousRun != previousCompletedRun && previousCompletedRun != null) {
                previousRun = previousCompletedRun;
            }
            comparisonBuildNumber = previousRun.getNumber();
            logger.println(Messages.PerfSigRecorder_LastSuccessfulBuild() + " #" + comparisonBuildNumber);
        } else {
            logger.println("No previous build found! No comparison possible!");
        }

        for (ConfigurationTestCase configurationTestCase : getConfigurationTestCases()) {
            if (!configurationTestCase.validate()) {
                throw new AbortException(Messages.PerfSigRecorder_TestCaseValidationError());
            }
            logger.println(String.format(Messages.PerfSigRecorder_ConnectionSuccessful(), configurationTestCase.getName()));

            final PerfSigEnvInvisAction buildEnvVars = getBuildEnvVars(build, configurationTestCase.getName());
            if (buildEnvVars != null) {
                sessionName = buildEnvVars.getSessionName();
            } else {
                throw new RESTErrorException("No sessionname found, aborting ...");
            }

            if (comparisonBuildNumber != 0) {
                final PerfSigEnvInvisAction otherEnvVars = getBuildEnvVars(previousRun, configurationTestCase.getName());
                if (otherEnvVars != null) {
                    comparisonSessionName = otherEnvVars.getSessionName();
                }
            }

            availableSessions = connection.getSessions();
            int retryCount = 0;
            while ((!validateSessionName(sessionName)) && (retryCount < serverConfiguration.getRetryCount())) {
                retryCount++;
                availableSessions = connection.getSessions();
                logger.println(String.format(Messages.PerfSigRecorder_WaitingForSession(), retryCount, serverConfiguration.getRetryCount()));
                Thread.sleep(10000);
            }

            if (!validateSessionName(sessionName)) {
                throw new RESTErrorException(String.format(Messages.PerfSigRecorder_SessionNotAvailable(), sessionName));
            }
            if (comparisonBuildNumber != 0 && !validateSessionName(comparisonSessionName)) {
                logger.println(String.format(Messages.PerfSigRecorder_ComparisonNotPossible(), comparisonSessionName));
            }

            for (Dashboard singleDashboard : configurationTestCase.getSingleDashboards()) {
                singleFilename = "Singlereport_" + sessionName + "_" + singleDashboard.getName() + ".pdf";
                logger.println(Messages.PerfSigRecorder_GettingPDFReport() + " " + singleFilename);
                boolean singleResult = connection.getPDFReport(sessionName, null, singleDashboard.getName(),
                        new File(PerfSigUtils.getReportDirectory(build), File.separator + singleFilename));
                if (!singleResult) {
                    throw new RESTErrorException(Messages.PerfSigRecorder_SingleReportError());
                }
            }
            for (Dashboard comparisonDashboard : configurationTestCase.getComparisonDashboards()) {
                if (comparisonBuildNumber != 0 && comparisonSessionName != null) {
                    comparisonFilename = "Comparisonreport_" + comparisonSessionName.replace(comparisonBuildNumber + "_",
                            buildNumber + "_" + comparisonBuildNumber + "_") + "_" + comparisonDashboard.getName() + ".pdf";
                    logger.println(Messages.PerfSigRecorder_GettingPDFReport() + " " + comparisonFilename);
                    boolean comparisonResult = connection.getPDFReport(sessionName, comparisonSessionName, comparisonDashboard.getName(),
                            new File(PerfSigUtils.getReportDirectory(build), File.separator + comparisonFilename));
                    if (!comparisonResult) {
                        throw new RESTErrorException(Messages.PerfSigRecorder_ComparisonReportError());
                    }
                }
            }
            logger.println(Messages.PerfSigRecorder_ParseXMLReport());
            final DashboardReport dashboardReport = connection.getDashboardReportFromXML(configurationTestCase.getXmlDashboard(), sessionName, configurationTestCase.getName());
            if (dashboardReport == null || dashboardReport.getChartDashlets() == null || dashboardReport.getChartDashlets().isEmpty()) {
                throw new RESTErrorException(Messages.PerfSigRecorder_XMLReportError());
            } else {
                dashboardReport.setUnitTest(configurationTestCase instanceof UnitTestCase);
                dashboardReports.add(dashboardReport);

                List<IncidentChart> incidents = dashboardReport.getIncidents();
                int numWarning = 0, numSevere = 0;
                if (incidents != null && incidents.size() > 0) {
                    logger.println("Following incidents occured:");
                    for (IncidentChart incident : incidents) {
                        for (IncidentViolation violation : incident.getViolations()) {
                            switch (violation.getSeverity()) {
                                case SEVERE:
                                    logger.println("Severe Incident:     " + incident.getRule() + " " + violation.getRule() + " " + violation.getDescription());
                                    numSevere++;
                                    break;
                                case WARNING:
                                    logger.println("Warning Incident:    " + incident.getRule() + " " + violation.getRule() + " " + violation.getDescription());
                                    numWarning++;
                                    break;
                                default:
                                    break;
                            }
                        }
                    }

                    switch (nonFunctionalFailure) {
                        case 1:
                            if (numSevere > 0) {
                                logger.println("builds status was set to 'failed' due to severe incidents");
                                build.setResult(Result.FAILURE);
                            }
                            break;
                        case 2:
                            if (numSevere > 0 || numWarning > 0) {
                                logger.println("builds status was set to 'failed' due to warning/severe incidents");
                                build.setResult(Result.FAILURE);
                            }
                            break;
                        case 3:
                            if (numSevere > 0) {
                                logger.println("builds status was set to 'unstable' due to severe incidents");
                                build.setResult(Result.UNSTABLE);
                            }
                            break;
                        case 4:
                            if (numSevere > 0 || numWarning > 0) {
                                logger.println("builds status was set to 'unstable' due to warning/severe incidents");
                                build.setResult(Result.UNSTABLE);
                            }
                            break;
                        default:
                            break;
                    }
                }
            }

            if (exportSessions) {
                boolean exportedSession = connection.downloadSession(sessionName, new File(PerfSigUtils.getReportDirectory(build) + File.separator + sessionName + ".dts"));
                if (!exportedSession) {
                    throw new RESTErrorException(Messages.PerfSigRecorder_SessionDownloadError());
                } else {
                    logger.println(Messages.PerfSigRecorder_SessionDownloadSuccessful());
                }
            }
        }

        build.addAction(new PerfSigBuildAction(build, dashboardReports));
        return true;
    }

    private PerfSigEnvInvisAction getBuildEnvVars(final Run<?, ?> build, final String testCase) {
        final List<PerfSigEnvInvisAction> envVars = build.getActions(PerfSigEnvInvisAction.class);
        for (PerfSigEnvInvisAction vars : envVars) {
            if (vars.getTestCase().equals(testCase))
                return vars;
        }
        return null;
    }

    private boolean validateSessionName(final String name) {
        return availableSessions.contains(name);
    }

    @Override
    public Action getProjectAction(final AbstractProject<?, ?> project) {
        return new PerfSigProjectAction(project);
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public boolean isExportSessions() {
        return exportSessions;
    }

    public List<ConfigurationTestCase> getConfigurationTestCases() {
        return configurationTestCases == null ? Collections.<ConfigurationTestCase>emptyList() : configurationTestCases;
    }

    public int getNonFunctionalFailure() {
        return nonFunctionalFailure;
    }

    public String getDynatraceProfile() {
        return dynatraceProfile;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public static final boolean defaultExportSessions = true;
        public static final int defaultNonFunctionalFailure = 0;
        private List<DynatraceServerConfiguration> configurations = new ArrayList<DynatraceServerConfiguration>();

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean configure(final StaplerRequest req, final JSONObject formData) throws FormException {
            configurations = req.bindJSONToList(DynatraceServerConfiguration.class, formData.get("configurations"));
            save();
            return false;
        }

        public ListBoxModel doFillDynatraceProfileItems() {
            return PerfSigUtils.listToListBoxModel(PerfSigUtils.getDTConfigurations());
        }

        public List<DynatraceServerConfiguration> getConfigurations() {
            return configurations;
        }

        public boolean isApplicable(final Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return Messages.PerfSigRecorder_DisplayName();
        }

        public DescriptorExtensionList<ConfigurationTestCase, Descriptor<ConfigurationTestCase>> getTestCaseTypes() {
            return ConfigurationTestCaseDescriptor.all();
        }
    }
}
