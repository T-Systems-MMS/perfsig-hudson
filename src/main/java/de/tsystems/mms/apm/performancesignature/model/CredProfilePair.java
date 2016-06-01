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

package de.tsystems.mms.apm.performancesignature.model;

import de.tsystems.mms.apm.performancesignature.Messages;
import de.tsystems.mms.apm.performancesignature.dynatrace.rest.CommandExecutionException;
import de.tsystems.mms.apm.performancesignature.dynatrace.rest.DTServerConnection;
import de.tsystems.mms.apm.performancesignature.util.PerfSigUtils;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Scrambler;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class CredProfilePair extends AbstractDescribableImpl<CredProfilePair> {
    private final String profile, username, password;

    @DataBoundConstructor
    public CredProfilePair(final String profile, final String username, final String password) {
        this.profile = profile;
        this.username = username;
        this.password = Scrambler.scramble(password);
    }

    public String getProfile() {
        return profile;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return Scrambler.descramble(password);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<CredProfilePair> {
        @Override
        public String getDisplayName() {
            return "";
        }

        public ListBoxModel doFillProfileItems(@RelativePath("..") @QueryParameter final String protocol, @RelativePath("..") @QueryParameter final String host,
                                               @RelativePath("..") @QueryParameter final int port, @QueryParameter final String username, @QueryParameter final String password,
                                               @RelativePath("..") @QueryParameter final boolean verifyCertificate, @RelativePath("..") @QueryParameter final boolean proxy,
                                               @RelativePath("..") @QueryParameter final String proxyServer, @RelativePath("..") @QueryParameter final int proxyPort,
                                               @RelativePath("..") @QueryParameter final String proxyUser, @RelativePath("..") @QueryParameter final String proxyPassword) {

            CustomProxy customProxyServer = null;
            if (proxy) {
                customProxyServer = new CustomProxy(proxyServer, proxyPort, proxyUser, proxyPassword, StringUtils.isBlank(proxyServer));
            }
            try {
                CredProfilePair pair = new CredProfilePair("", username, password);
                final DTServerConnection connection = new DTServerConnection(protocol, host, port, pair, verifyCertificate, customProxyServer);
                return PerfSigUtils.listToListBoxModel(connection.getSystemProfiles());
            } catch (CommandExecutionException ignored) {
                return null;
            }
        }

        public FormValidation doTestDynaTraceConnection(@QueryParameter final String protocol, @QueryParameter final String host,
                                                        @QueryParameter final int port, @QueryParameter final String username, @QueryParameter final String password,
                                                        @QueryParameter final boolean verifyCertificate, @QueryParameter final boolean proxy,
                                                        @QueryParameter final String proxyServer, @QueryParameter final int proxyPort,
                                                        @QueryParameter final String proxyUser, @QueryParameter final String proxyPassword) {

            CustomProxy customProxyServer = null;
            if (proxy) {
                customProxyServer = new CustomProxy(proxyServer, proxyPort, proxyUser, proxyPassword, StringUtils.isBlank(proxyServer));
            }
            CredProfilePair pair = new CredProfilePair("", username, password);
            final DTServerConnection connection = new DTServerConnection(protocol, host, port, pair, verifyCertificate, customProxyServer);

            if (connection.validateConnection()) {
                return FormValidation.ok(Messages.PerfSigRecorder_TestConnectionSuccessful());
            } else {
                return FormValidation.warning(Messages.PerfSigRecorder_TestConnectionNotSuccessful());
            }
        }
    }
}
