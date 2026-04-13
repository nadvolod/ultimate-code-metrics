package com.utm.temporal;

import com.utm.temporal.activity.*;
import com.utm.temporal.agent.FindingDispositionAgent;
import com.utm.temporal.agent.LearningAgent;
import com.utm.temporal.db.DatabaseClient;
import com.utm.temporal.github.GitHubClient;
import com.utm.temporal.workflow.*;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.schedules.*;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

import java.time.Duration;
import java.util.Collections;

/**
 * Long-running worker for the learning system.
 * Registers Temporal Schedules and processes learning workflows.
 *
 * Usage: java com.utm.temporal.LearningWorkerApp <repository>
 *
 * Environment variables:
 *   POSTGRES_URL   — Neon Postgres connection string
 *   GITHUB_TOKEN   — GitHub API token
 *   OPENAI_API_KEY — OpenAI API key
 *   TEMPORAL_ADDRESS — Temporal server (default: localhost:7233)
 */
public class LearningWorkerApp {
    private static final String TASK_QUEUE = "learning";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: LearningWorkerApp <repository>");
            System.err.println("  repository: owner/repo (e.g., nadvolod/ultimate-code-metrics)");
            System.exit(1);
        }

        String repository = args[0];

        // Connect to Temporal server
        String temporalAddress = System.getenv().getOrDefault("TEMPORAL_ADDRESS", "localhost:7233");
        System.out.println("Connecting to Temporal server at " + temporalAddress + "...");

        WorkflowServiceStubs service;
        if ("localhost:7233".equals(temporalAddress)) {
            service = WorkflowServiceStubs.newLocalServiceStubs();
        } else {
            service = WorkflowServiceStubs.newServiceStubs(
                    WorkflowServiceStubsOptions.newBuilder()
                            .setTarget(temporalAddress)
                            .build());
        }

        WorkflowClient client = WorkflowClient.newInstance(service);

        // Register schedules
        System.out.println("Registering schedules for " + repository + "...");
        ScheduleClient scheduleClient = ScheduleClient.newInstance(service);
        registerSchedules(scheduleClient, repository);

        // Create and start worker
        System.out.println("Starting learning worker on task queue: " + TASK_QUEUE);
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(TASK_QUEUE);

        // Register workflow implementations
        worker.registerWorkflowImplementationTypes(
                OutcomeCollectionWorkflowImpl.class,
                LearningWorkflowImpl.class,
                EvaluationWorkflowImpl.class
        );

        // Create dependencies
        DatabaseClient dbClient = new DatabaseClient();
        GitHubClient gitHubClient = new GitHubClient();
        LearningAgent learningAgent = new LearningAgent();
        FindingDispositionAgent dispositionAgent = new FindingDispositionAgent();

        // Register activity implementations
        worker.registerActivitiesImplementations(
                new GitHubOutcomeActivityImpl(gitHubClient, dbClient),
                new FindingDispositionActivityImpl(dispositionAgent, gitHubClient, dbClient),
                new LearningActivityImpl(learningAgent, dbClient),
                new CreateLearningPRActivityImpl(dbClient, gitHubClient),
                new EvaluationActivityImpl(dbClient)
        );

        // Start worker — blocks forever (daemon mode)
        factory.start();
        System.out.println("Learning worker started. Schedules registered. Press Ctrl+C to stop.");
        System.out.println("View schedules at: http://localhost:8233/schedules");

        // Keep the process alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("Learning worker shutting down...");
            factory.shutdown();
            service.shutdown();
        }
    }

    private static void registerSchedules(ScheduleClient scheduleClient, String repository) {
        // 1. Outcome Collection — every hour
        registerSchedule(scheduleClient,
                "outcome-collection-hourly",
                OutcomeCollectionWorkflow.class,
                "outcome-collection",
                repository,
                "0 * * * *",
                "Collect PR outcomes from GitHub every hour");

        // 2. Learning Analysis — daily at 3 AM UTC
        registerSchedule(scheduleClient,
                "learning-daily",
                LearningWorkflow.class,
                "learning-analysis",
                repository,
                "0 3 * * *",
                "Analyze outcomes and propose learning improvements daily");

        // 3. Evaluation — weekly Monday 6 AM UTC
        registerSchedule(scheduleClient,
                "evaluation-weekly",
                EvaluationWorkflow.class,
                "evaluation",
                repository,
                "0 6 * * MON",
                "Compute weekly evaluation metrics snapshot");
    }

    private static void registerSchedule(ScheduleClient scheduleClient,
                                           String scheduleId,
                                           Class<?> workflowClass,
                                           String workflowIdPrefix,
                                           String repository,
                                           String cron,
                                           String description) {
        try {
            ScheduleActionStartWorkflow action = ScheduleActionStartWorkflow.newBuilder()
                    .setWorkflowType(workflowClass)
                    .setArguments(repository)
                    .setOptions(WorkflowOptions.newBuilder()
                            .setWorkflowId(workflowIdPrefix + "-" + repository.replace("/", "-"))
                            .setTaskQueue(TASK_QUEUE)
                            .build())
                    .build();

            ScheduleSpec spec = ScheduleSpec.newBuilder()
                    .setCronExpressions(Collections.singletonList(cron))
                    .build();

            Schedule schedule = Schedule.newBuilder()
                    .setAction(action)
                    .setSpec(spec)
                    .build();

            scheduleClient.createSchedule(scheduleId, schedule, ScheduleOptions.newBuilder().build());
            System.out.println("  ✓ Registered: " + scheduleId + " (" + cron + ") — " + description);

        } catch (ScheduleAlreadyRunningException e) {
            System.out.println("  ○ Already exists: " + scheduleId + " (skipping)");
        }
    }
}
