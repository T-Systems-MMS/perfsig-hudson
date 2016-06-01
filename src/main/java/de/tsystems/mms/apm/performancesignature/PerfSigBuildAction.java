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
import hudson.model.Action;
import hudson.model.Run;
import org.kohsuke.stapler.StaplerProxy;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PerfSigBuildAction extends PerfSigBaseAction implements StaplerProxy {
    private final List<DashboardReport> dashboardReports;
    private transient Run<?, ?> run;
    private transient WeakReference<PerfSigBuildActionResultsDisplay> buildActionResultsDisplay;

    public PerfSigBuildAction(final Run<?, ?> run, final List<DashboardReport> dashboardReports) {
        this.run = run;
        this.dashboardReports = dashboardReports;
    }

    public PerfSigBuildActionResultsDisplay getBuildActionResultsDisplay() {
        PerfSigBuildActionResultsDisplay buildDisplay;
        WeakReference<PerfSigBuildActionResultsDisplay> wr = this.buildActionResultsDisplay;
        if (wr != null) {
            buildDisplay = wr.get();
            if (buildDisplay != null) {
                return buildDisplay;
            }
        }
        buildDisplay = new PerfSigBuildActionResultsDisplay(this);
        this.buildActionResultsDisplay = new WeakReference<PerfSigBuildActionResultsDisplay>(buildDisplay);
        return buildDisplay;
    }

    public PerfSigBuildActionResultsDisplay getTarget() {
        return getBuildActionResultsDisplay();
    }

    @Override
    protected String getTitle() {
        return run.getDisplayName() + " PerfSig";
    }

    public Run<?, ?> getBuild() {
        return this.run;
    }

    public List<DashboardReport> getDashboardReports() {
        return this.dashboardReports;
    }

    public Collection<? extends Action> getProjectActions() {
        return Collections.singleton(new PerfSigProjectAction(run.getParent()));
    }
}
