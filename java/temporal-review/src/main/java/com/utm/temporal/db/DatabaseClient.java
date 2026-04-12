package com.utm.temporal.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utm.temporal.model.*;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseClient {
    private final String connectionUrl;
    private final ObjectMapper objectMapper;

    public DatabaseClient() {
        this(System.getenv("POSTGRES_URL"));
    }

    public DatabaseClient(String connectionUrl) {
        this.connectionUrl = connectionUrl;
        this.objectMapper = new ObjectMapper();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(connectionUrl);
    }

    // ============================================================
    // Outcome Recording (Phase 2)
    // ============================================================

    public int upsertPullRequest(String repository, int prNumber, String prTitle,
                                  String prDescription, String author) throws SQLException {
        String sql = "INSERT INTO pull_requests (repository, pr_number, pr_title, pr_description, author, status) " +
                     "VALUES (?, ?, ?, ?, ?, 'OPEN') " +
                     "ON CONFLICT (repository, pr_number) DO UPDATE SET pr_title = EXCLUDED.pr_title " +
                     "RETURNING id";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, repository);
            stmt.setInt(2, prNumber);
            stmt.setString(3, prTitle);
            stmt.setString(4, prDescription);
            stmt.setString(5, author);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    public int saveReviewRun(int pullRequestId, String reviewId, int learningVersion,
                              String overallRecommendation, List<AgentResult> agentResults,
                              long tookMs, String model) throws SQLException {
        String agentResultsJson;
        try {
            agentResultsJson = objectMapper.writeValueAsString(agentResults);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize agent results", e);
        }

        String sql = "INSERT INTO review_runs (pull_request_id, review_id, learning_version, " +
                     "overall_recommendation, agent_results_json, took_ms, model) " +
                     "VALUES (?, ?, ?, ?, ?::jsonb, ?, ?) RETURNING id";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, pullRequestId);
            stmt.setString(2, reviewId);
            stmt.setInt(3, learningVersion);
            stmt.setString(4, overallRecommendation);
            stmt.setString(5, agentResultsJson);
            stmt.setLong(6, tookMs);
            stmt.setString(7, model);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    public void saveFindings(int reviewRunId, List<AgentResult> agentResults) throws SQLException {
        String sql = "INSERT INTO findings (review_run_id, agent_name, risk_level, finding_text, recommendation) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (AgentResult result : agentResults) {
                if (result.findings == null) continue;
                for (String finding : result.findings) {
                    stmt.setInt(1, reviewRunId);
                    stmt.setString(2, result.agentName);
                    stmt.setString(3, result.riskLevel);
                    stmt.setString(4, finding);
                    stmt.setString(5, result.recommendation);
                    stmt.addBatch();
                }
            }
            stmt.executeBatch();
        }
    }

    // ============================================================
    // Outcome Collection (Phase 3)
    // ============================================================

    public List<ReviewOutcome> loadPendingOutcomes(String repository) throws SQLException {
        String sql = "SELECT rr.review_id, pr.pr_number, pr.pr_title, pr.author, " +
                     "rr.overall_recommendation, rr.reviewed_at, rr.learning_version " +
                     "FROM review_runs rr " +
                     "JOIN pull_requests pr ON rr.pull_request_id = pr.id " +
                     "WHERE pr.repository = ? AND pr.status = 'OPEN'";
        List<ReviewOutcome> outcomes = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, repository);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ReviewOutcome outcome = new ReviewOutcome();
                outcome.reviewId = rs.getString("review_id");
                outcome.repository = repository;
                outcome.prNumber = rs.getInt("pr_number");
                outcome.prTitle = rs.getString("pr_title");
                outcome.author = rs.getString("author");
                outcome.systemRecommendation = rs.getString("overall_recommendation");
                outcome.reviewedAt = rs.getString("reviewed_at");
                outcome.learningVersion = rs.getInt("learning_version");
                outcomes.add(outcome);
            }
        }
        return outcomes;
    }

    public void updatePullRequestStatus(String repository, int prNumber,
                                         String status, String mergedAt, String closedAt) throws SQLException {
        String sql = "UPDATE pull_requests SET status = ?, merged_at = ?::timestamptz, closed_at = ?::timestamptz " +
                     "WHERE repository = ? AND pr_number = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setString(2, mergedAt);
            stmt.setString(3, closedAt);
            stmt.setString(4, repository);
            stmt.setInt(5, prNumber);
            stmt.executeUpdate();
        }
    }

    public void savePostMergeOutcome(int pullRequestId, boolean hadRevert, Integer revertPrNumber,
                                      boolean hadFollowUpFixes) throws SQLException {
        String sql = "INSERT INTO post_merge_outcomes (pull_request_id, had_revert, revert_pr_number, had_follow_up_fixes) " +
                     "VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, pullRequestId);
            stmt.setBoolean(2, hadRevert);
            if (revertPrNumber != null) {
                stmt.setInt(3, revertPrNumber);
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            stmt.setBoolean(4, hadFollowUpFixes);
            stmt.executeUpdate();
        }
    }

    public void saveFindingOutcome(int findingId, String disposition, String evidence) throws SQLException {
        String sql = "INSERT INTO finding_outcomes (finding_id, disposition, evidence) " +
                     "VALUES (?, ?, ?) " +
                     "ON CONFLICT (finding_id) DO UPDATE SET disposition = EXCLUDED.disposition, " +
                     "evidence = EXCLUDED.evidence, determined_at = NOW()";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, findingId);
            stmt.setString(2, disposition);
            stmt.setString(3, evidence);
            stmt.executeUpdate();
        }
    }

    // ============================================================
    // Learning Insights (Phase 4 + 5)
    // ============================================================

    public int getCurrentLearningVersion(String repository) throws SQLException {
        String sql = "SELECT COALESCE(MAX(version), 0) FROM learning_versions WHERE repository = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, repository);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    public LearningInsights loadInsights(String repository) throws SQLException {
        int version = getCurrentLearningVersion(repository);
        if (version == 0) return null;

        LearningInsights insights = new LearningInsights();
        insights.repository = repository;
        insights.learningVersion = version;
        insights.activeHeuristics = loadActiveHeuristics(repository);
        insights.activePromptPatches = loadActivePromptPatches(repository);
        insights.activeCalibrations = loadActiveCalibrations(repository);
        insights.agentAccuracyStats = loadAgentAccuracy(repository, version);
        return insights;
    }

    public List<LearnedHeuristic> loadActiveHeuristics(String repository) throws SQLException {
        String sql = "SELECT id, agent_name, heuristic_type, rule::text, description, evidence, " +
                     "learning_version, activated_version " +
                     "FROM learned_heuristics WHERE repository = ? AND status = 'APPROVED'";
        List<LearnedHeuristic> heuristics = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, repository);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                LearnedHeuristic h = new LearnedHeuristic();
                h.id = rs.getInt("id");
                h.repository = repository;
                h.agentName = rs.getString("agent_name");
                h.heuristicType = rs.getString("heuristic_type");
                h.rule = rs.getString("rule");
                h.description = rs.getString("description");
                h.evidence = rs.getString("evidence");
                h.learningVersion = rs.getInt("learning_version");
                h.activatedVersion = rs.getInt("activated_version");
                h.status = "APPROVED";
                heuristics.add(h);
            }
        }
        return heuristics;
    }

    public List<PromptPatch> loadActivePromptPatches(String repository) throws SQLException {
        String sql = "SELECT id, agent_name, patch_type, description, patch_content, evidence, " +
                     "learning_version, activated_version " +
                     "FROM prompt_patch_suggestions WHERE repository = ? AND status = 'APPROVED'";
        List<PromptPatch> patches = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, repository);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                PromptPatch p = new PromptPatch();
                p.id = rs.getInt("id");
                p.repository = repository;
                p.agentName = rs.getString("agent_name");
                p.patchType = rs.getString("patch_type");
                p.description = rs.getString("description");
                p.patchContent = rs.getString("patch_content");
                p.evidence = rs.getString("evidence");
                p.learningVersion = rs.getInt("learning_version");
                p.activatedVersion = rs.getInt("activated_version");
                p.status = "APPROVED";
                patches.add(p);
            }
        }
        return patches;
    }

    public List<SeverityCalibration> loadActiveCalibrations(String repository) throws SQLException {
        String sql = "SELECT id, agent_name, category, original_level, calibrated_level, " +
                     "confidence, sample_size, learning_version, activated_version " +
                     "FROM severity_calibrations WHERE repository = ? AND status = 'APPROVED'";
        List<SeverityCalibration> calibrations = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, repository);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                SeverityCalibration c = new SeverityCalibration();
                c.id = rs.getInt("id");
                c.repository = repository;
                c.agentName = rs.getString("agent_name");
                c.category = rs.getString("category");
                c.originalLevel = rs.getString("original_level");
                c.calibratedLevel = rs.getString("calibrated_level");
                c.confidence = rs.getDouble("confidence");
                c.sampleSize = rs.getInt("sample_size");
                c.learningVersion = rs.getInt("learning_version");
                c.activatedVersion = rs.getInt("activated_version");
                c.status = "APPROVED";
                calibrations.add(c);
            }
        }
        return calibrations;
    }

    public Map<String, AgentAccuracy> loadAgentAccuracy(String repository, int learningVersion) throws SQLException {
        String sql = "SELECT agent_name, total_findings, accepted_findings, dismissed_findings, " +
                     "deferred_findings, precision_rate " +
                     "FROM agent_precision_profiles WHERE repository = ? AND learning_version = ?";
        Map<String, AgentAccuracy> stats = new HashMap<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, repository);
            stmt.setInt(2, learningVersion);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                AgentAccuracy a = new AgentAccuracy();
                a.agentName = rs.getString("agent_name");
                a.totalFindings = rs.getInt("total_findings");
                a.acceptedFindings = rs.getInt("accepted_findings");
                a.dismissedFindings = rs.getInt("dismissed_findings");
                a.deferredFindings = rs.getInt("deferred_findings");
                a.precisionRate = rs.getDouble("precision_rate");
                stats.put(a.agentName, a);
            }
        }
        return stats;
    }

    // ============================================================
    // Learning Aggregation Queries (Phase 4)
    // ============================================================

    public Map<String, AgentAccuracy> computeAgentAccuracy(String repository) throws SQLException {
        String sql = "SELECT f.agent_name, " +
                     "COUNT(*) AS total, " +
                     "COUNT(*) FILTER (WHERE fo.disposition = 'ACCEPTED') AS accepted, " +
                     "COUNT(*) FILTER (WHERE fo.disposition = 'DISMISSED') AS dismissed, " +
                     "COUNT(*) FILTER (WHERE fo.disposition = 'DEFERRED') AS deferred " +
                     "FROM findings f " +
                     "JOIN review_runs rr ON f.review_run_id = rr.id " +
                     "JOIN pull_requests pr ON rr.pull_request_id = pr.id " +
                     "JOIN finding_outcomes fo ON f.id = fo.finding_id " +
                     "WHERE pr.repository = ? AND fo.disposition != 'UNKNOWN' " +
                     "GROUP BY f.agent_name";
        Map<String, AgentAccuracy> stats = new HashMap<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, repository);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                AgentAccuracy a = new AgentAccuracy();
                a.agentName = rs.getString("agent_name");
                a.totalFindings = rs.getInt("total");
                a.acceptedFindings = rs.getInt("accepted");
                a.dismissedFindings = rs.getInt("dismissed");
                a.deferredFindings = rs.getInt("deferred");
                int denominator = a.acceptedFindings + a.dismissedFindings;
                a.precisionRate = denominator > 0 ? (double) a.acceptedFindings / denominator : 0.0;
                stats.put(a.agentName, a);
            }
        }
        return stats;
    }

    public List<FindingOutcome> loadAllFindingsWithOutcomes(String repository) throws SQLException {
        String sql = "SELECT f.agent_name, f.finding_text, f.risk_level, fo.disposition, fo.evidence " +
                     "FROM findings f " +
                     "JOIN review_runs rr ON f.review_run_id = rr.id " +
                     "JOIN pull_requests pr ON rr.pull_request_id = pr.id " +
                     "JOIN finding_outcomes fo ON f.id = fo.finding_id " +
                     "WHERE pr.repository = ? AND fo.disposition != 'UNKNOWN' " +
                     "ORDER BY f.agent_name, fo.disposition";
        List<FindingOutcome> findings = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, repository);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                FindingOutcome fo = new FindingOutcome();
                fo.agentName = rs.getString("agent_name");
                fo.finding = rs.getString("finding_text");
                fo.riskLevel = rs.getString("risk_level");
                fo.disposition = rs.getString("disposition");
                fo.evidence = rs.getString("evidence");
                findings.add(fo);
            }
        }
        return findings;
    }

    public int countReviewsForRepo(String repository) throws SQLException {
        String sql = "SELECT COUNT(*) FROM review_runs rr " +
                     "JOIN pull_requests pr ON rr.pull_request_id = pr.id " +
                     "WHERE pr.repository = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, repository);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    // ============================================================
    // Learning Proposals (Phase 4)
    // ============================================================

    public void saveHeuristic(LearnedHeuristic h) throws SQLException {
        String sql = "INSERT INTO learned_heuristics (repository, agent_name, heuristic_type, rule, " +
                     "description, evidence, status, learning_version) " +
                     "VALUES (?, ?, ?, ?::jsonb, ?, ?, 'PROPOSED', ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, h.repository);
            stmt.setString(2, h.agentName);
            stmt.setString(3, h.heuristicType);
            stmt.setString(4, h.rule);
            stmt.setString(5, h.description);
            stmt.setString(6, h.evidence);
            stmt.setInt(7, h.learningVersion);
            stmt.executeUpdate();
        }
    }

    public void savePromptPatch(PromptPatch p) throws SQLException {
        String sql = "INSERT INTO prompt_patch_suggestions (repository, agent_name, patch_type, " +
                     "description, patch_content, evidence, status, learning_version) " +
                     "VALUES (?, ?, ?, ?, ?, ?, 'PROPOSED', ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, p.repository);
            stmt.setString(2, p.agentName);
            stmt.setString(3, p.patchType);
            stmt.setString(4, p.description);
            stmt.setString(5, p.patchContent);
            stmt.setString(6, p.evidence);
            stmt.setInt(7, p.learningVersion);
            stmt.executeUpdate();
        }
    }

    public void saveSeverityCalibration(SeverityCalibration c) throws SQLException {
        String sql = "INSERT INTO severity_calibrations (repository, agent_name, category, " +
                     "original_level, calibrated_level, confidence, sample_size, status, learning_version) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, 'PROPOSED', ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, c.repository);
            stmt.setString(2, c.agentName);
            stmt.setString(3, c.category);
            stmt.setString(4, c.originalLevel);
            stmt.setString(5, c.calibratedLevel);
            stmt.setDouble(6, c.confidence);
            stmt.setInt(7, c.sampleSize);
            stmt.setInt(8, c.learningVersion);
            stmt.executeUpdate();
        }
    }

    public void saveAgentPrecisionProfile(String repository, String agentName, int learningVersion,
                                           AgentAccuracy accuracy) throws SQLException {
        String sql = "INSERT INTO agent_precision_profiles (repository, agent_name, learning_version, " +
                     "total_findings, accepted_findings, dismissed_findings, deferred_findings, precision_rate) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT (repository, agent_name, learning_version) DO UPDATE SET " +
                     "total_findings = EXCLUDED.total_findings, accepted_findings = EXCLUDED.accepted_findings, " +
                     "dismissed_findings = EXCLUDED.dismissed_findings, precision_rate = EXCLUDED.precision_rate, " +
                     "computed_at = NOW()";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, repository);
            stmt.setString(2, agentName);
            stmt.setInt(3, learningVersion);
            stmt.setInt(4, accuracy.totalFindings);
            stmt.setInt(5, accuracy.acceptedFindings);
            stmt.setInt(6, accuracy.dismissedFindings);
            stmt.setInt(7, accuracy.deferredFindings);
            stmt.setDouble(8, accuracy.precisionRate);
            stmt.executeUpdate();
        }
    }

    // ============================================================
    // Version Management (Phase 5)
    // ============================================================

    public int bumpLearningVersion(String repository, String description, String createdBy) throws SQLException {
        int nextVersion = getCurrentLearningVersion(repository) + 1;
        String sql = "INSERT INTO learning_versions (repository, version, description, created_by) " +
                     "VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, repository);
            stmt.setInt(2, nextVersion);
            stmt.setString(3, description);
            stmt.setString(4, createdBy);
            stmt.executeUpdate();
        }
        return nextVersion;
    }

    public void activateProposals(String repository, int activatedVersion) throws SQLException {
        try (Connection conn = getConnection()) {
            String[] tables = {"learned_heuristics", "severity_calibrations", "prompt_patch_suggestions"};
            for (String table : tables) {
                String sql = "UPDATE " + table + " SET status = 'APPROVED', activated_version = ?, " +
                             "approved_at = NOW() WHERE repository = ? AND status = 'PROPOSED'";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, activatedVersion);
                    stmt.setString(2, repository);
                    stmt.executeUpdate();
                }
            }
        }
    }

    // ============================================================
    // Findings for disposition inference (Phase 3)
    // ============================================================

    public List<FindingOutcome> loadFindingsForReview(String reviewId) throws SQLException {
        String sql = "SELECT f.id, f.agent_name, f.finding_text, f.risk_level " +
                     "FROM findings f " +
                     "JOIN review_runs rr ON f.review_run_id = rr.id " +
                     "LEFT JOIN finding_outcomes fo ON f.id = fo.finding_id " +
                     "WHERE rr.review_id = ? AND fo.id IS NULL";
        List<FindingOutcome> findings = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, reviewId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                FindingOutcome fo = new FindingOutcome();
                fo.findingId = rs.getInt("id");
                fo.agentName = rs.getString("agent_name");
                fo.finding = rs.getString("finding_text");
                fo.riskLevel = rs.getString("risk_level");
                fo.disposition = "UNKNOWN";
                findings.add(fo);
            }
        }
        return findings;
    }

    public int getPullRequestId(String repository, int prNumber) throws SQLException {
        String sql = "SELECT id FROM pull_requests WHERE repository = ? AND pr_number = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, repository);
            stmt.setInt(2, prNumber);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
            return -1;
        }
    }
}
