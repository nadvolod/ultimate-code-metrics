package com.utm.temporal;

import com.utm.temporal.activity.*;
import com.utm.temporal.agent.ComplexityAgent;
import com.utm.temporal.agent.CodeQualityAgent;
import com.utm.temporal.agent.PriorityAgent;
import com.utm.temporal.agent.SecurityAgent;
import com.utm.temporal.agent.TestQualityAgent;
import com.utm.temporal.workflow.PRReviewWorkflowImpl;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

/**
 * Long-running Temporal Worker process.
 *
 * This process connects to the Temporal server, registers workflows and activities,
 * and polls for tasks indefinitely. Run this in a separate terminal from the Starter.
 *
 * Usage:
 *   cd java/temporal-review
 *   mvn exec:java@worker
 *
 * To demonstrate Temporal durability:
 * 1. Start this worker
 * 2. Submit a workflow via Starter (in another terminal)
 * 3. Stop this worker mid-execution (Ctrl+C)
 * 4. Restart this worker
 * 5. Observe the workflow resumes from where it left off
 */
public class WorkerApp {
    private static final String TASK_QUEUE = "pr-review";

    public static void main(String[] args) {
        System.out.println("Starting Temporal Worker...");
        System.out.println("Task Queue: " + TASK_QUEUE);

        // Connect to Temporal server
        System.out.println("Connecting to Temporal server at localhost:7233...");
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);

        // Create worker factory and worker
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(TASK_QUEUE);

        // Register workflow implementation
        worker.registerWorkflowImplementationTypes(PRReviewWorkflowImpl.class);

        // Create agent instances for dependency injection
        CodeQualityAgent codeQualityAgent = new CodeQualityAgent();
        TestQualityAgent testQualityAgent = new TestQualityAgent();
        SecurityAgent securityAgent = new SecurityAgent();
        PriorityAgent priorityAgent = new PriorityAgent();
        ComplexityAgent complexityAgent = new ComplexityAgent();

        // Register activity implementations
        worker.registerActivitiesImplementations(
                new CodeQualityActivityImpl(codeQualityAgent),
                new TestQualityActivityImpl(testQualityAgent),
                new SecurityQualityActivityImpl(securityAgent),
                new PriorityActivityImpl(priorityAgent),
                new ComplexityQualityActivityImpl(complexityAgent));

        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down worker...");
            factory.shutdown();
            service.shutdown();
            System.out.println("Worker stopped.");
        }));

        // Start worker (this blocks and polls for tasks)
        factory.start();

        System.out.println("Worker started successfully!");
        System.out.println("Listening for workflow tasks on queue: " + TASK_QUEUE);
        System.out.println("Press Ctrl+C to stop the worker.");
        System.out.println();

        // Block indefinitely - worker runs until process is terminated
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
