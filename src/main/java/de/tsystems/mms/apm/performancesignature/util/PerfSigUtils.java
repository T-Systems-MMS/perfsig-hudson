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

package de.tsystems.mms.apm.performancesignature.util;

import de.tsystems.mms.apm.performancesignature.PerfSigRecorder;
import de.tsystems.mms.apm.performancesignature.dynatrace.model.Agent;
import de.tsystems.mms.apm.performancesignature.dynatrace.model.BaseConfiguration;
import de.tsystems.mms.apm.performancesignature.dynatrace.rest.CommandExecutionException;
import de.tsystems.mms.apm.performancesignature.model.CredProfilePair;
import de.tsystems.mms.apm.performancesignature.model.DynatraceServerConfiguration;
import hudson.FilePath;
import hudson.Functions;
import hudson.model.Hudson;
import hudson.model.Run;
import hudson.util.Area;
import hudson.util.ListBoxModel;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.List;

public class PerfSigUtils {
    private PerfSigUtils() {
    }

    /**
     * @return {@link hudson.model.Hudson#getInstance()} if that isn't null, or die.
     */
    public static Hudson getInstanceOrDie() {
        final Hudson hudson = Hudson.getInstance();
        if (hudson == null) {
            throw new IllegalStateException("Jenkins is not running");
        }
        return hudson;
    }

    public static BigDecimal round(final double d, final int decimalPlace) {
        if (d == 0) return BigDecimal.valueOf(0);
        BigDecimal bd = new BigDecimal(d);
        bd = bd.setScale(d % 1 == 0 ? 0 : decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd;
    }

    public static ListBoxModel listToListBoxModel(final List<?> arrayList) {
        final ListBoxModel listBoxModel = new ListBoxModel();
        for (Object item : arrayList) {
            if (item instanceof String)
                listBoxModel.add((String) item);
            else if (item instanceof Agent)
                listBoxModel.add(((Agent) item).getName());
            else if (item instanceof DynatraceServerConfiguration) {
                DynatraceServerConfiguration conf = (DynatraceServerConfiguration) item;
                if (CollectionUtils.isNotEmpty(conf.getCredProfilePairs()))
                    for (CredProfilePair credProfilePair : conf.getCredProfilePairs()) {
                        String listItem = credProfilePair.getProfile() + " (" + credProfilePair.getUsername() + ") @ " +
                                conf.getName();
                        listBoxModel.add(listItem);
                    }
            } else if (item instanceof BaseConfiguration)
                listBoxModel.add(((BaseConfiguration) item).getId());
        }
        return listBoxModel;
    }

    public static File getReportDirectory(final Run<?, ?> run) throws IOException {
        File reportDirectory = new File(run.getRootDir(), Messages.PerfSigUtils_ReportDirectory());
        if (!reportDirectory.exists()) {
            if (!reportDirectory.mkdirs()) throw new IOException("failed to create report directory");
        }
        return reportDirectory;
    }

    public static List<DynatraceServerConfiguration> getDTConfigurations() {
        return getInstanceOrDie().getDescriptorByType(PerfSigRecorder.DescriptorImpl.class).getConfigurations();
    }

    public static DynatraceServerConfiguration getServerConfiguration(final String dynatraceServer) {
        for (DynatraceServerConfiguration serverConfiguration : getDTConfigurations()) {
            String strippedName = dynatraceServer.replaceAll(".*@", "").trim();
            if (strippedName.equals(serverConfiguration.getName())) {
                return serverConfiguration;
            }
        }
        return null;
    }

    public static List<FilePath> getDownloadFiles(final String testCase, final Run<?, ?> build) throws IOException, InterruptedException {
        FilePath filePath = new FilePath(PerfSigUtils.getReportDirectory(build));
        return filePath.list(new RegexFileFilter(testCase));
    }

    public static void downloadFile(final StaplerRequest request, final StaplerResponse response, final Run build) throws IOException {
        final String file = request.getParameter("f");
        if (file.matches("[^a-zA-Z0-9\\._-]+")) return;
        File downloadFile = new File(PerfSigUtils.getReportDirectory(build), File.separator + file);
        FileInputStream inStream = new FileInputStream(downloadFile);

        // gets MIME type of the file
        String mimeType;
        if (file.contains("pdf")) mimeType = "application/pdf";
        else mimeType = "application/octet-stream"; // set to binary type if MIME mapping not found

        try {
            // forces download
            String headerKey = "Content-Disposition";
            String headerValue = String.format("attachment; filename=\"%s\"", file);
            response.setHeader(headerKey, headerValue);
            response.serveFile(request, inStream, downloadFile.lastModified(), 604800000, (int) downloadFile.length(), "mime-type:" + mimeType);
        } catch (ServletException e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(inStream);
        }
    }

    /*
    Change this in case of new Dashboardstuff
    Used for rewriting diagram titles from Time to WebService-Time etc.
    */
    public static String generateTitle(final String measure, final String chartDashlet) {
        if (StringUtils.deleteWhitespace(measure).equalsIgnoreCase(StringUtils.deleteWhitespace(chartDashlet)))
            return chartDashlet;
        else
            return chartDashlet + " - " + measure;
    }

    public static String encodeString(final String value) {
        if (value == null) return null;
        try {
            return URLEncoder.encode(value, "UTF-8").replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new CommandExecutionException(Messages.PerfSigUtils_EncodingFailure(), e);
        }
    }

    public static String getDurationString(final float seconds) {
        int minutes = (int) ((seconds % 3600) / 60);
        float rest = seconds % 60;
        return minutes + " min " + (int) rest + " s";
    }

    public static Area calcDefaultSize() {
        Area res = Functions.getScreenResolution();
        if (res != null && res.width <= 800)
            return new Area(250, 100);
        else
            return new Area(500, 200);
    }
}
