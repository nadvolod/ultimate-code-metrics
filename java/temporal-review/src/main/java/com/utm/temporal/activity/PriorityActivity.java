package com.utm.temporal.activity;

import com.utm.temporal.model.AgentResult;
import com.utm.temporal.model.ReviewRequest;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

@ActivityInterface
public interface PriorityActivity {
    @ActivityMethod
    AgentResult prioritizeIssues(ReviewRequest request, List<AgentResult> agentResults);
}
