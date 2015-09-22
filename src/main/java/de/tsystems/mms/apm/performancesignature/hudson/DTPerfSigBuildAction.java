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

package de.tsystems.mms.apm.performancesignature.hudson;

import de.tsystems.mms.apm.performancesignature.dynatrace.model.DashboardReport;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import org.kohsuke.stapler.StaplerProxy;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Created by rapi on 25.04.2014.
 */

public class DTPerfSigBuildAction implements Action, StaplerProxy {
    private final AbstractBuild<?, ?> build;
    private final List<DashboardReport> dashboardReports;
    private transient WeakReference<DTPerfSigBuildActionResultsDisplay> buildActionResultsDisplay;

    public DTPerfSigBuildAction(final AbstractBuild<?, ?> build, final List<DashboardReport> dashboardReports) {
        this.build = build;
        this.dashboardReports = dashboardReports;
    }

    @Override
    public String toString() {
        return "DTPerfSigBuildAction{ build=" + build + '}';
    }

    @SuppressWarnings("unchecked")
    public DTPerfSigBuildActionResultsDisplay getBuildActionResultsDisplay() {
        DTPerfSigBuildActionResultsDisplay buildDisplay;
        WeakReference<DTPerfSigBuildActionResultsDisplay> wr = this.buildActionResultsDisplay;
        if (wr != null) {
            buildDisplay = wr.get();
            if (buildDisplay != null) {
                return buildDisplay;
            }
        }
        buildDisplay = new DTPerfSigBuildActionResultsDisplay(this);
        this.buildActionResultsDisplay = new WeakReference(buildDisplay);
        return buildDisplay;
    }

    public DTPerfSigBuildActionResultsDisplay getTarget() {
        return getBuildActionResultsDisplay();
    }

    public String getIconFileName() {
        return "/plugin/" + Messages.DTPerfSigBuildAction_UrlName() + "/images/icon.png";
    }

    public String getDisplayName() {
        return Messages.DTPerfSigBuildAction_DisplayName();
    }

    public String getUrlName() {
        return Messages.DTPerfSigBuildAction_UrlName();
    }

    public AbstractBuild<?, ?> getBuild() {
        return this.build;
    }

    public List<DashboardReport> getDashboardReports() {
        return this.dashboardReports;
    }
}
