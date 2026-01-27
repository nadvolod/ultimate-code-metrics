package com.utm.temporal;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utm.temporal.model.ReviewRequest;
import com.utm.temporal.model.ReviewResponse;
import com.utm.temporal.workflow.PRReviewWorkflow;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;

/**
 * CLI entrypoint for submitting PR review workflows to Temporal.
 *
 * This is an ephemeral process that submits a workflow and waits for the result.
 * The actual execution happens on the Worker process.
 *
 * Usage:
 *   cd java/temporal-review
 *   mvn exec:java@workflow -Dexec.args="../../sample-input.json ../../sample-output.json"
 *
 * Exit codes:
 * 0 - Success
 * 1 - Invalid arguments
 * 2 - Workflow execution failed
 * 3 - File I/O error
 */
public class Starter {
    private static final String TASK_QUEUE = "pr-review";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        // Validate arguments
        if (args.length != 2) {
            System.err.println("Usage: Starter <input-json-path> <output-json-path>");
            System.exit(1);
        }

        String inputPath = args[0];
        String outputPath = args[1];

        WorkflowServiceStubs service = null;
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
            service = WorkflowServiceStubs.newLocalServiceStubs();
            WorkflowClient client = WorkflowClient.newInstance(service);

            // Create workflow stub and execute
            String workflowId = TASK_QUEUE + "-" + UUID.randomUUID();
            System.out.println("Starting workflow: " + workflowId);

            PRReviewWorkflow workflow = client.newWorkflowStub(
                    PRReviewWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setTaskQueue(TASK_QUEUE)
                            .setWorkflowId(workflowId)
                            .build());

            // Execute workflow (blocks until completion)
            System.out.println("Executing workflow... (this may take a while)");
            System.out.println("You can monitor progress at: http://localhost:8233/namespaces/default/workflows/" + workflowId);
            ReviewResponse response = workflow.review(request);

            // Write output JSON
            System.out.println("Writing output to: " + outputPath);
            String outputJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(response);
            Files.writeString(new File(outputPath).toPath(), outputJson);

            System.out.println();
            System.out.println("Review completed successfully!");
            System.out.println("Overall recommendation: " + response.overallRecommendation);
            System.exit(0);

        } catch (Exception e) {
            System.err.println("Error executing workflow: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        } finally {
            // Cleanup gRPC resources
            if (service != null) {
                service.shutdown();
            }
        }
    }
}
