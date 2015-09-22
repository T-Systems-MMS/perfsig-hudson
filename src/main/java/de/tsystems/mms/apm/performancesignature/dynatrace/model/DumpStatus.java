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
 * Created by rapi on 01.07.2015.
 */
public class DumpStatus {
    private String result = null;
    private String success = null;
    private String messageText = null;

    public String getResultValue() {
        return this.result;
    }

    public boolean isResultValueTrue() {
        return (this.result != null) && (this.result.equalsIgnoreCase("true"));
    }

    public String getSuccess() {
        return this.success;
    }

    public boolean isSuccessTrue() {
        return (this.success != null) && (this.success.equalsIgnoreCase("true"));
    }

    public String getMessageText() {
        return this.messageText;
    }

    public void setValue(final String property, final String prevProperty, final String value) {
        if (property.equalsIgnoreCase("result")) {
            this.result = value;
        } else if (property.equalsIgnoreCase("success")) {
            this.success = value;
        } else if ((prevProperty.equalsIgnoreCase("message")) && (property.equalsIgnoreCase("text"))) {
            this.messageText = value;
        }
    }
}

