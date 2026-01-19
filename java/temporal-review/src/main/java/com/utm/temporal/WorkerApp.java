package com.utm.temporal;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utm.temporal.activity.CodeQualityActivityImpl;
import com.utm.temporal.activity.PriorityActivityImpl;
import com.utm.temporal.activity.SecurityQualityActivityImpl;
import com.utm.temporal.activity.TestQualityActivityImpl;
import com.utm.temporal.agent.CodeQualityAgent;
import com.utm.temporal.agent.PriorityAgent;
import com.utm.temporal.agent.SecurityAgent;
import com.utm.temporal.agent.TestQualityAgent;
import com.utm.temporal.model.ReviewRequest;
import com.utm.temporal.model.ReviewResponse;
import com.utm.temporal.workflow.PRReviewWorkflow;
import com.utm.temporal.workflow.PRReviewWorkflowImpl;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

/**
 * CLI entrypoint for running PR reviews via Temporal.
 *
 * Usage:
 * java com.utm.temporal.RunReview <input-json-path> <output-json-path>
 *
 * Exit codes:
 * 0 - Success
 * 1 - Invalid arguments
 * 2 - Workflow execution failed
 * 3 - File I/O error
 */
public class WorkerApp {
    private static final String TASK_QUEUE = "pr-review";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        // Validate arguments
        if (args.length != 2) {
            System.err.println("Usage: RunReview <input-json-path> <output-json-path>");
            System.exit(1);
        }

        String inputPath = args[0];
        String outputPath = args[1];

        try {
            // Read input JSON
            System.out.println("Reading input from: " + inputPath);
            File inputFile = new File(inputPath);
            if (!inputFile.exists()) {
                System.err.println("Error: Input file does not exist: " + inputPath);
                System.exit(3);
            }

            String inputJson = Files.readString(inputFile.toPath());
            ReviewRequest request = objectMapper.readValue(inputJson, ReviewRequest.class);

            // Connect to Temporal server
            System.out.println("Connecting to Temporal server...");
            WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
            WorkflowClient client = WorkflowClient.newInstance(service);

            // Create and start worker in background
            System.out.println("Starting Temporal worker...");
            WorkerFactory factory = WorkerFactory.newInstance(client);
            Worker worker = factory.newWorker(TASK_QUEUE);

            // Register workflow implementation
            worker.registerWorkflowImplementationTypes(PRReviewWorkflowImpl.class);

            // Create agent instances for dependency injection
            CodeQualityAgent codeQualityAgent = new CodeQualityAgent();
            TestQualityAgent testQualityAgent = new TestQualityAgent();
            SecurityAgent securityAgent = new SecurityAgent();
            PriorityAgent priorityAgent = new PriorityAgent();

            // Register activity implementations
            worker.registerActivitiesImplementations(
                    new CodeQualityActivityImpl(codeQualityAgent),
                    new TestQualityActivityImpl(testQualityAgent),
                    new SecurityQualityActivityImpl(securityAgent),
                    new PriorityActivityImpl(priorityAgent));

            // Start worker in background
            factory.start();

            // Create workflow stub and execute
            System.out.println("Starting workflow execution...");
            String workflowId = TASK_QUEUE + "-" + UUID.randomUUID();
            PRReviewWorkflow workflow = client.newWorkflowStub(
                    PRReviewWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setTaskQueue(TASK_QUEUE)
                            .setWorkflowId(workflowId)
                            .build());

            // Execute workflow (blocks until completion)
            ReviewResponse response = workflow.review(request);

            // Write output JSON
            System.out.println("Writing output to: " + outputPath);
            String outputJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(response);
            Files.writeString(new File(outputPath).toPath(), outputJson);

            // Shutdown worker
            factory.shutdown();
            service.shutdown();

            System.out.println("Review completed successfully");
            System.out.println("Overall recommendation: " + response.overallRecommendation);
            System.exit(0);

        } catch (Exception e) {
            System.err.println("Error executing workflow: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }
}
