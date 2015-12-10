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

import de.tsystems.mms.apm.performancesignature.dynatrace.model.DashboardReport;
import de.tsystems.mms.apm.performancesignature.dynatrace.model.IncidentChart;
import de.tsystems.mms.apm.performancesignature.dynatrace.model.IncidentViolation;
import de.tsystems.mms.apm.performancesignature.dynatrace.rest.DTServerConnection;
import de.tsystems.mms.apm.performancesignature.model.ConfigurationTestCase;
import de.tsystems.mms.apm.performancesignature.model.ConfigurationTestCase.ConfigurationTestCaseDescriptor;
import de.tsystems.mms.apm.performancesignature.model.CustomProxy;
import de.tsystems.mms.apm.performancesignature.model.Dashboard;
import de.tsystems.mms.apm.performancesignature.util.PerfSigUtils;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PerfSigRecorder extends Recorder {
    private final String protocol, host, profile, username, password;
    private final boolean verifyCertificate;
    private final boolean exportSessions;
    private final boolean modifyBuildResult;
    private final boolean technicalFailure;
    private final int delay;
    private final int retryCount;
    private final int port;
    private final int nonFunctionalFailure;
    private final List<ConfigurationTestCase> configurationTestCases;
    private final CustomProxy customProxy;
    private transient List<String> availableSessions;

    @DataBoundConstructor
    public PerfSigRecorder(final String protocol, final String host, final String username, final String password, final int port, final String profile,
                           final boolean verifyCertificate, final boolean exportSessions, boolean technicalFailure, final int delay, final int retryCount,
                           final List<ConfigurationTestCase> configurationTestCases, final boolean modifyBuildResult,
                           final boolean proxy, final CustomProxy proxySource, final JSONObject nonFunctionalFailure) {
        this.protocol = protocol;
        this.host = host;
        this.modifyBuildResult = modifyBuildResult;
        this.username = username;
        this.password = password;
        this.port = port;
        this.profile = profile;
        this.verifyCertificate = verifyCertificate;
        this.exportSessions = exportSessions;
        this.delay = delay;
        this.retryCount = retryCount;
        this.configurationTestCases = configurationTestCases;
        if (modifyBuildResult) {
            this.technicalFailure = technicalFailure;
            this.nonFunctionalFailure = nonFunctionalFailure.getInt("value");
        } else {
            this.technicalFailure = false;
            this.nonFunctionalFailure = 0;
        }
        if (proxy) {
            this.customProxy = proxySource;
        } else {
            this.customProxy = null;
        }
    }

    @Override
    public boolean perform(final AbstractBuild build, final Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
        final PrintStream logger = listener.getLogger();

        final DTServerConnection connection = new DTServerConnection(this.getProtocol(), this.getHost(), this.getPort(),
                this.getUsername(), this.getPassword(), verifyCertificate, customProxy);
        logger.println(Messages.PerfSigRecorder_VerifyDTConnection());
        if (!connection.validateConnection()) {
            logger.println(Messages.PerfSigRecorder_DTConnectionError());
            checkForUnstableResult(build);
            return !isTechnicalFailure();
        }

        if (configurationTestCases == null) {
            logger.println(Messages.PerfSigRecorder_MissingTestCases());
        }

        if (this.delay != 0) {
            logger.println(Messages.PerfSigRecorder_SleepingDelay() + " " + this.getDelay() + " sec");
            Thread.sleep(this.getDelay() * 1000);
        }
        logger.println(Messages.PerfSigRecorder_ReportDirectory() + " " + PerfSigUtils.getReportDirectory(build));

        String sessionName, comparisonSessionName = null, singleFilename, comparisonFilename;
        int comparisonBuildNumber = 0;
        final int buildNumber = build.getNumber();
        final List<DashboardReport> dashboardReports = new ArrayList<DashboardReport>();

        Run previousBuildRun = build.getPreviousNotFailedBuild();
        if (previousBuildRun != null) {
            if (build.getPreviousCompletedBuild() != previousBuildRun && build.getPreviousCompletedBuild() != null) {
                previousBuildRun = build.getPreviousCompletedBuild();
            }
            comparisonBuildNumber = previousBuildRun.getNumber();
            logger.println(Messages.PerfSigRecorder_LastSuccessfulBuild() + " #" + comparisonBuildNumber);
        } else {
            logger.println("No previous build found! No comparison possible!");
        }

        for (ConfigurationTestCase configurationTestCase : getConfigurationTestCases()) {
            if (!configurationTestCase.validate()) {
                logger.println(Messages.PerfSigRecorder_TestCaseValidationError());
                checkForUnstableResult(build);
                return !isTechnicalFailure();
            }
            logger.println(String.format(Messages.PerfSigRecorder_ConnectionSuccessful(), configurationTestCase.getName()));

            final PerfSigRegisterEnvVars buildEnvVars = getBuildEnvVars(build, configurationTestCase.getName());
            if (buildEnvVars != null) {
                sessionName = buildEnvVars.getSessionName();
            } else {
                logger.println("No sessionname found, aborting ...");
                checkForUnstableResult(build);
                return !isTechnicalFailure();
            }

            if (comparisonBuildNumber != 0) {
                final PerfSigRegisterEnvVars otherEnvVars = getBuildEnvVars(previousBuildRun, configurationTestCase.getName());
                if (otherEnvVars != null) {
                    comparisonSessionName = otherEnvVars.getSessionName();
                }
            }

            try {
                availableSessions = connection.getSessions();
                int retryCount = 0;
                while ((!validateSessionName(sessionName)) && (retryCount < getRetryCount())) {
                    retryCount++;
                    availableSessions = connection.getSessions();
                    logger.println(String.format(Messages.PerfSigRecorder_WaitingForSession(), retryCount, getRetryCount()));
                    Thread.sleep(10000);
                }
            } catch (Exception e) {
                logger.println(e);
                return !isTechnicalFailure();
            }

            if (!validateSessionName(sessionName)) {
                logger.println(String.format(Messages.PerfSigRecorder_SessionNotAvailable(), sessionName));
                checkForUnstableResult(build);
                continue;
            }
            if (comparisonBuildNumber != 0 && !validateSessionName(comparisonSessionName)) {
                logger.println(String.format(Messages.PerfSigRecorder_ComparisonNotPossible(), comparisonSessionName));
            }

            try {
                for (Dashboard singleDashboard : configurationTestCase.getSingleDashboards()) {
                    singleFilename = "Singlereport_" + sessionName + "_" + singleDashboard.getName() + ".pdf";
                    logger.println(Messages.PerfSigRecorder_GettingPDFReport() + " " + singleFilename);
                    boolean singleResult = connection.getPDFReport(sessionName, null, singleDashboard.getName(),
                            new File(PerfSigUtils.getReportDirectory(build) + File.separator + singleFilename));
                    if (!singleResult) {
                        logger.println(Messages.PerfSigRecorder_SingleReportError());
                        checkForUnstableResult(build);
                    }
                }
                for (Dashboard comparisonDashboard : configurationTestCase.getComparisonDashboards()) {
                    if (comparisonBuildNumber != 0 && comparisonSessionName != null && getBuildResult(build).isBetterThan(Result.FAILURE)) {
                        comparisonFilename = "Comparisonreport_" + comparisonSessionName.replace(comparisonBuildNumber + "_",
                                buildNumber + "_" + comparisonBuildNumber + "_") + "_" + comparisonDashboard.getName() + ".pdf";
                        logger.println(Messages.PerfSigRecorder_GettingPDFReport() + " " + comparisonFilename);
                        boolean comparisonResult = connection.getPDFReport(sessionName, comparisonSessionName, comparisonDashboard.getName(),
                                new File(PerfSigUtils.getReportDirectory(build) + File.separator + comparisonFilename));
                        if (!comparisonResult) {
                            logger.println(Messages.PerfSigRecorder_ComparisonReportError());
                            checkForUnstableResult(build);
                        }
                    }
                }
                logger.println(Messages.PerfSigRecorder_ParseXMLReport());
                final DashboardReport dashboardReport = connection.getDashboardReportFromXML(configurationTestCase.getXmlDashboard(), sessionName, configurationTestCase.getName());
                if (dashboardReport == null || dashboardReport.getChartDashlets() == null || dashboardReport.getChartDashlets().isEmpty()) {
                    logger.println(Messages.PerfSigRecorder_XMLReportError());
                    checkForUnstableResult(build);
                } else {
                    dashboardReport.setConfigurationTestCase(configurationTestCase);
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
                        logger.println(Messages.PerfSigRecorder_SessionDownloadError());
                        checkForUnstableResult(build);
                    } else {
                        logger.println(Messages.PerfSigRecorder_SessionDownloadSuccessful());
                    }
                }
            } catch (Exception e) {
                logger.println(e);
                return !isTechnicalFailure();
            }
        }

        PerfSigBuildAction action = new PerfSigBuildAction(build, dashboardReports);
        build.addAction(action);
        return true;
    }

    private PerfSigRegisterEnvVars getBuildEnvVars(final Run build, final String testCase) {
        final List<PerfSigRegisterEnvVars> envVars = build.getActions(PerfSigRegisterEnvVars.class);
        for (PerfSigRegisterEnvVars vars : envVars) {
            if (vars.getTestCase().equals(testCase))
                return vars;
        }
        return null;
    }

    private boolean validateSessionName(final String name) {
        return availableSessions.contains(name);
    }

    private void checkForUnstableResult(final AbstractBuild build) {
        if (isTechnicalFailure()) build.setResult(Result.FAILURE);
    }

    @Override
    public Action getProjectAction(final AbstractProject<?, ?> project) {
        return new PerfSigProjectAction(project);
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public String getProtocol() {
        return this.protocol;
    }

    public String getHost() {
        return this.host;
    }

    public int getPort() {
        return this.port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getProfile() {
        return this.profile;
    }

    public int getDelay() {
        return this.delay;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public CustomProxy getCustomProxy() {
        return customProxy;
    }

    public boolean isVerifyCertificate() {
        return verifyCertificate;
    }

    public boolean isExportSessions() {
        return exportSessions;
    }

    public boolean isModifyBuildResult() {
        return modifyBuildResult;
    }

    private Result getBuildResult(final AbstractBuild build) {
        Result result = build.getResult();
        if (result == null) {
            throw new IllegalStateException("build is ongoing");
        }
        return result;
    }

    @Nonnull
    public List<ConfigurationTestCase> getConfigurationTestCases() {
        return configurationTestCases == null ? Collections.<ConfigurationTestCase>emptyList() : configurationTestCases;
    }

    public boolean isTechnicalFailure() {
        return technicalFailure;
    }

    public int getNonFunctionalFailure() {
        return nonFunctionalFailure;
    }

    /**
     * Descriptor for {@link PerfSigRecorder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        public static String getDefaultUsername() {
            return "admin";
        }

        public static String getDefaultPassword() {
            return "admin";
        }

        public static String getDefaultHost() {
            return Messages.PerfSigRecorder_DefaultAddress();
        }

        public static int getDefaultPort() {
            return Integer.parseInt(Messages.PerfSigRecorder_DefaultPort());
        }

        public static int getDefaultDelay() {
            return Integer.parseInt(Messages.PerfSigRecorder_DefaultDelay());
        }

        public static int getDefaultRetryCount() {
            return Integer.parseInt(Messages.PerfSigRecorder_DefaultRetryCount());
        }

        public static boolean getDefaultVerifyCertificate() {
            return Boolean.valueOf("false");
        }

        public static boolean getDefaultExportSessions() {
            return Boolean.valueOf(Messages.PerfSigRecorder_DefaultExportSessions());
        }

        public static boolean getDefaultModifyBuildResult() {
            return Boolean.valueOf(Messages.PerfSigRecorder_DefaultModifyBuildResult());
        }

        protected static boolean checkNotNullOrEmpty(final String string) {
            return StringUtils.isNotBlank(string);
        }

        protected static boolean checkNotEmptyAndIsNumber(final String number) {
            return StringUtils.isNotBlank(number) && NumberUtils.isNumber(number);
        }

        public FormValidation doCheckHost(@QueryParameter final String host) {
            FormValidation validationResult;
            if (checkNotNullOrEmpty(host)) {
                validationResult = FormValidation.ok();
            } else {
                validationResult = FormValidation.error(Messages.PerfSigRecorder_DTHostNotValid());
            }
            return validationResult;
        }

        public FormValidation doCheckPort(@QueryParameter final String port) {
            FormValidation validationResult;
            if (checkNotEmptyAndIsNumber(port)) {
                validationResult = FormValidation.ok();
            } else {
                validationResult = FormValidation.error(Messages.PerfSigRecorder_DTPortNotValid());
            }
            return validationResult;
        }

        public FormValidation doCheckCredentialsId(@QueryParameter final String credentialsId) {
            FormValidation validationResult;
            if (checkNotNullOrEmpty(credentialsId)) {
                validationResult = FormValidation.ok();
            } else {
                validationResult = FormValidation.error(Messages.PerfSigRecorder_DTUserEmpty());
            }
            return validationResult;
        }

        public FormValidation doCheckProfile(@QueryParameter final String profile) {
            FormValidation validationResult;
            if (checkNotNullOrEmpty(profile)) {
                validationResult = FormValidation.ok();
            } else {
                validationResult = FormValidation.error(Messages.PerfSigRecorder_DTProfileNotValid());
            }
            return validationResult;
        }

        public FormValidation doCheckDelay(@QueryParameter final String delay) {
            FormValidation validationResult;
            if (checkNotEmptyAndIsNumber(delay)) {
                validationResult = FormValidation.ok();
            } else {
                validationResult = FormValidation.error(Messages.PerfSigRecorder_DelayNotValid());
            }
            return validationResult;
        }

        public FormValidation doCheckRetryCount(@QueryParameter final String retryCount) {
            FormValidation validationResult;
            if (checkNotEmptyAndIsNumber(retryCount)) {
                validationResult = FormValidation.ok();
            } else {
                validationResult = FormValidation.error(Messages.PerfSigRecorder_RetryCountNotValid());
            }
            return validationResult;
        }

        public FormValidation doTestDynaTraceConnection(@QueryParameter final String protocol, @QueryParameter final String host,
                                                        @QueryParameter final int port, @QueryParameter final String username,
                                                        @QueryParameter final String password,
                                                        @QueryParameter final boolean verifyCertificate, @QueryParameter final boolean proxy,
                                                        @QueryParameter final int proxySource,
                                                        @QueryParameter final String proxyServer, @QueryParameter final int proxyPort,
                                                        @QueryParameter final String proxyUser, @QueryParameter final String proxyPassword) {

            CustomProxy customProxyServer = null;
            if (proxy) {
                customProxyServer = new CustomProxy(proxyServer, proxyPort, proxyUser, proxyPassword, proxySource == 0);
            }
            final DTServerConnection connection = new DTServerConnection(protocol, host, port, username, password, verifyCertificate, customProxyServer);

            if (connection.validateConnection()) {
                return FormValidation.ok(Messages.PerfSigRecorder_TestConnectionSuccessful());
            } else {
                return FormValidation.warning(Messages.PerfSigRecorder_TestConnectionNotSuccessful());
            }
        }

        public boolean isApplicable(final Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return Messages.PerfSigRecorder_DisplayName();
        }

        public ListBoxModel doFillProtocolItems() {
            return new ListBoxModel(new Option("https"), new Option("http"));
        }

        public ListBoxModel doFillProfileItems(@QueryParameter final String protocol, @QueryParameter final String host,
                                               @QueryParameter final int port, @QueryParameter final String username,
                                               @QueryParameter final String password,
                                               @QueryParameter final boolean verifyCertificate, @QueryParameter final boolean proxy,
                                               @QueryParameter final int proxySource,
                                               @QueryParameter final String proxyServer, @QueryParameter final int proxyPort,
                                               @QueryParameter final String proxyUser, @QueryParameter final String proxyPassword) {

            CustomProxy customProxyServer = null;
            if (proxy) {
                customProxyServer = new CustomProxy(proxyServer, proxyPort, proxyUser, proxyPassword, proxySource == 0);
            }
            final DTServerConnection connection = new DTServerConnection(protocol, host, port, username, password, verifyCertificate, customProxyServer);
            return PerfSigUtils.listToListBoxModel(connection.getProfiles());
        }

        public List<ConfigurationTestCaseDescriptor> getTestCaseTypes(final AbstractProject<?, ?> project) {
            return ConfigurationTestCaseDescriptor.all((Class<? extends AbstractProject<?, ?>>) project.getClass());
        }

        public List<ConfigurationTestCaseDescriptor> getTestCaseTypes() {
            return ConfigurationTestCaseDescriptor.all();
        }
    }
}
