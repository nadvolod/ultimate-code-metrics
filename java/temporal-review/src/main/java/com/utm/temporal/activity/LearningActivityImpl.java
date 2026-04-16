package com.utm.temporal.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.utm.temporal.agent.LearningAgent;
import com.utm.temporal.agent.LearningAgent.LearningProposals;
import com.utm.temporal.db.DatabaseClient;
import com.utm.temporal.model.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class LearningActivityImpl implements LearningActivity {
    private static final Logger logger = LoggerFactory.getLogger(LearningActivityImpl.class);

    private final LearningAgent agent;
    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    public LearningActivityImpl(LearningAgent agent, DatabaseClient databaseClient) {
        this.agent = agent;
        this.databaseClient = databaseClient;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public int analyzeOutcomes(String repository) {
        try {
            // 1. Compute stats from DB (deterministic)
            Map<String, AgentAccuracy> accuracyStats = databaseClient.computeAgentAccuracy(repository);
            List<FindingOutcome> allFindings = databaseClient.loadAllFindingsWithOutcomes(repository);
            int totalReviews = databaseClient.countReviewsForRepo(repository);

            int currentVersion = databaseClient.getCurrentLearningVersion(repository);

            if (totalReviews < 5) {
                writeLearningStatus(repository, currentVersion, totalReviews,
                        accuracyStats,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList());
                return currentVersion; // Not enough data to learn from
            }

            // 2. Use LLM for pattern analysis (semantic)
            LearningProposals proposals = agent.analyze(repository, accuracyStats, allFindings, totalReviews);

            // 3. Save proposals to DB (all as PROPOSED)
            for (LearnedHeuristic h : proposals.heuristics) {
                h.learningVersion = currentVersion;
                databaseClient.saveHeuristic(h);
            }
            for (PromptPatch p : proposals.promptPatches) {
                p.learningVersion = currentVersion;
                databaseClient.savePromptPatch(p);
            }
            for (SeverityCalibration c : proposals.calibrations) {
                c.learningVersion = currentVersion;
                databaseClient.saveSeverityCalibration(c);
            }

            // 4. Save agent precision profiles
            for (Map.Entry<String, AgentAccuracy> entry : accuracyStats.entrySet()) {
                databaseClient.saveAgentPrecisionProfile(
                        repository, entry.getKey(), currentVersion, entry.getValue());
            }

            // 5. Write learning status snapshot for the UI dashboard
            List<LearnedHeuristic> activeHeuristics = databaseClient.loadActiveHeuristics(repository);
            List<PromptPatch> activePatches = databaseClient.loadActivePromptPatches(repository);
            List<SeverityCalibration> activeCalibrations = databaseClient.loadActiveCalibrations(repository);
            writeLearningStatus(repository, currentVersion, totalReviews,
                    accuracyStats, activeHeuristics, activePatches, activeCalibrations);

            return currentVersion;

        } catch (Exception e) {
            throw new RuntimeException("Learning analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Writes a learning status snapshot to {@code data/learning/status.json} so the
     * Next.js dashboard can display real-time learning progress without a direct DB
     * connection.  Failures here are logged but never propagate — the workflow must
     * not be disrupted by a file I/O problem.
     */
    private void writeLearningStatus(String repository,
                                      int learningVersion,
                                      int totalReviews,
                                      Map<String, AgentAccuracy> accuracyStats,
                                      List<LearnedHeuristic> activeHeuristics,
                                      List<PromptPatch> activePromptPatches,
                                      List<SeverityCalibration> activeCalibrations) {
        try {
            Instant now = Instant.now();
            // Next occurrence of 3 AM UTC — mirrors the "0 3 * * *" cron in LearningWorkerApp
            Instant nextDailyRun = now.truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS)
                    .plus(3, ChronoUnit.HOURS);

            Map<String, Object> status = new LinkedHashMap<>();
            status.put("repository", repository);
            status.put("learningVersion", learningVersion);
            status.put("lastRunAt", now.toString());
            status.put("nextRunAt", nextDailyRun.toString());
            status.put("totalReviewsAnalyzed", totalReviews);

            // Schedules (static — reflects LearningWorkerApp configuration)
            Map<String, Object> schedules = new LinkedHashMap<>();
            schedules.put("outcomeCollection", scheduleEntry("0 * * * *",
                    "Collect PR outcomes from GitHub every hour", now.toString(), "OK"));
            schedules.put("learningAnalysis", scheduleEntry("0 3 * * *",
                    "Analyze outcomes and propose learning improvements daily", now.toString(), "OK"));
            // Evaluation runs on a separate weekly schedule (EvaluationWorkflow / EvaluationActivityImpl).
            // Its lastRunAt is not known here, so it is left null and shown as PENDING until the
            // EvaluationActivity updates the status file independently.
            schedules.put("evaluation", scheduleEntry("0 6 * * MON",
                    "Compute weekly evaluation metrics snapshot", null, "PENDING"));
            status.put("schedules", schedules);

            // Agent accuracy
            Map<String, Object> accuracyMap = new LinkedHashMap<>();
            for (Map.Entry<String, AgentAccuracy> entry : accuracyStats.entrySet()) {
                AgentAccuracy a = entry.getValue();
                Map<String, Object> acc = new LinkedHashMap<>();
                acc.put("agentName", a.agentName);
                acc.put("totalFindings", a.totalFindings);
                acc.put("acceptedFindings", a.acceptedFindings);
                acc.put("dismissedFindings", a.dismissedFindings);
                acc.put("deferredFindings", a.deferredFindings);
                acc.put("precisionRate", a.precisionRate);
                accuracyMap.put(entry.getKey(), acc);
            }
            status.put("agentAccuracy", accuracyMap);

            // Active heuristics
            List<Map<String, Object>> heuristicsList = new ArrayList<>();
            for (LearnedHeuristic h : activeHeuristics) {
                Map<String, Object> hMap = new LinkedHashMap<>();
                hMap.put("id", h.id);
                hMap.put("agentName", h.agentName);
                hMap.put("heuristicType", h.heuristicType);
                hMap.put("description", h.description);
                hMap.put("evidence", h.evidence);
                hMap.put("status", h.status);
                hMap.put("learningVersion", h.learningVersion);
                heuristicsList.add(hMap);
            }
            status.put("activeHeuristics", heuristicsList);

            // Active prompt patches
            List<Map<String, Object>> patchList = new ArrayList<>();
            for (PromptPatch p : activePromptPatches) {
                Map<String, Object> pMap = new LinkedHashMap<>();
                pMap.put("id", p.id);
                pMap.put("agentName", p.agentName);
                pMap.put("patchType", p.patchType);
                pMap.put("description", p.description);
                pMap.put("evidence", p.evidence);
                pMap.put("status", p.status);
                pMap.put("learningVersion", p.learningVersion);
                patchList.add(pMap);
            }
            status.put("activePromptPatches", patchList);

            // Active calibrations
            List<Map<String, Object>> calibList = new ArrayList<>();
            for (SeverityCalibration c : activeCalibrations) {
                Map<String, Object> cMap = new LinkedHashMap<>();
                cMap.put("id", c.id);
                cMap.put("agentName", c.agentName);
                cMap.put("category", c.category);
                cMap.put("originalLevel", c.originalLevel);
                cMap.put("calibratedLevel", c.calibratedLevel);
                cMap.put("confidence", c.confidence);
                cMap.put("sampleSize", c.sampleSize);
                cMap.put("status", c.status);
                cMap.put("learningVersion", c.learningVersion);
                calibList.add(cMap);
            }
            status.put("activeCalibrations", calibList);

            // Write atomically via a temp file: objectMapper writes to a .tmp file first, then
            // ATOMIC_MOVE swaps it into place.  This ensures the Next.js API never reads a
            // partially-written file even if a concurrent request is in progress.
            Path outputDir = Paths.get("data", "learning");
            Files.createDirectories(outputDir);
            Path tmp = Files.createTempFile(outputDir, "status-", ".tmp");
            try {
                objectMapper.writeValue(tmp.toFile(), status);
                Files.move(tmp, outputDir.resolve("status.json"), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                logger.info("Learning status written to data/learning/status.json (v{})", learningVersion);
            } catch (Exception writeEx) {
                Files.deleteIfExists(tmp);
                throw writeEx;
            }

        } catch (Exception e) {
            // Never fail the workflow due to a status-file write problem
            logger.warn("Failed to write learning status file: {}", e.getMessage(), e);
        }
    }

    private static Map<String, Object> scheduleEntry(String cron, String description,
                                                       String lastRunAt, String statusStr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cron", cron);
        m.put("description", description);
        m.put("lastRunAt", lastRunAt);
        m.put("status", statusStr);
        return m;
    }
}
