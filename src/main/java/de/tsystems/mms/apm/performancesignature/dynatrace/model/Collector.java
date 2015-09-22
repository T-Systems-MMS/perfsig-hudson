/*
 * Copyright (c) 2008-2015, DYNATRACE LLC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice,
 *       this list of conditions and the following disclaimer in the documentation
 *       and/or other materials provided with the distribution.
 *     * Neither the name of the dynaTrace software nor the names of its contributors
 *       may be used to endorse or promote products derived from this software without
 *       specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
 * DAMAGE.
 */

package de.tsystems.mms.apm.performancesignature.dynatrace.model;

/**
 * Created by rapi on 27.10.2014.
 */
public class Collector {
    private boolean connected, embedded;
    private String host, name;

    public void setValue(final String property, final String value) {
        if (property.equalsIgnoreCase(Messages.Collector_PropHost())) {
            setHost(value);
        } else if (property.equalsIgnoreCase(Messages.Collector_PropName())) {
            setName(value);
        } else if (property.equalsIgnoreCase(Messages.Collector_PropConnected())) {
            setConnected(Boolean.valueOf(value));
        } else if (property.equalsIgnoreCase(Messages.Collector_PropEmbedded())) {
            setEmbedded(Boolean.valueOf(value));
        }
    }

    public boolean isConnected() {
        return this.connected;
    }

    public void setConnected(final boolean connected) {
        this.connected = connected;
    }

    public boolean isEmbedded() {
        return this.embedded;
    }

    public void setEmbedded(final boolean embedded) {
        this.embedded = embedded;
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getHost() {
        return this.host;
    }

    public void setHost(final String host) {
        this.host = host;
    }

    public String toString() {
        return "Name=" + this.name + ";Host=" + this.host + ";Embedded=" + this.embedded;
    }
}
