package com.utm.temporal.model;

public class SeverityCalibration {
    public int id;
    public String repository;
    public String agentName;
    public String category;
    public String originalLevel;
    public String calibratedLevel;
    public double confidence;
    public int sampleSize;
    public String status;        // PROPOSED, APPROVED
    public int learningVersion;
    public Integer activatedVersion;

    public SeverityCalibration() {}
}
