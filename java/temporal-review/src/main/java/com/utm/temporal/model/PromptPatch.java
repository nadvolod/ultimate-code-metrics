package com.utm.temporal.model;

public class PromptPatch {
    public int id;
    public String repository;
    public String agentName;
    public String patchType;     // ADD_INSTRUCTION, REMOVE_INSTRUCTION, MODIFY_CRITERIA
    public String description;
    public String patchContent;
    public String evidence;
    public String status;        // PROPOSED, APPROVED, REJECTED
    public int learningVersion;
    public Integer activatedVersion;

    public PromptPatch() {}
}
