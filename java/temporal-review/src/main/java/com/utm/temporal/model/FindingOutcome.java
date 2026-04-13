package com.utm.temporal.model;

public class FindingOutcome {
    public int findingId;
    public String agentName;
    public String finding;
    public String riskLevel;
    public String disposition;  // ACCEPTED, DISMISSED, DEFERRED, UNKNOWN
    public String evidence;

    public FindingOutcome() {}

    public FindingOutcome(String agentName, String finding, String riskLevel, String disposition) {
        this.agentName = agentName;
        this.finding = finding;
        this.riskLevel = riskLevel;
        this.disposition = disposition;
    }
}
