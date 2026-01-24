package com.utm.temporal.activity;

import com.utm.temporal.agent.PriorityAgent;
import com.utm.temporal.model.AgentResult;
import com.utm.temporal.model.ReviewRequest;

import java.util.List;

public class PriorityActivityImpl implements PriorityActivity {
    private final PriorityAgent priorityAgent;

    public PriorityActivityImpl(PriorityAgent priorityAgent) {
        this.priorityAgent = priorityAgent;
    }

    @Override
    public AgentResult prioritizeIssues(ReviewRequest request, List<AgentResult> agentResults) {
        return this.priorityAgent.prioritize(request, agentResults);
    }
}
