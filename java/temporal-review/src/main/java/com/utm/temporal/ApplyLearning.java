package com.utm.temporal;

import com.utm.temporal.db.DatabaseClient;

/**
 * CLI utility called by the GitHub Action when a learning PR is merged.
 * Reads the repository from environment, activates all PROPOSED changes,
 * and bumps the learning version.
 *
 * Usage: java com.utm.temporal.ApplyLearning <repository>
 */
public class ApplyLearning {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: ApplyLearning <repository>");
            System.exit(1);
        }

        String repository = args[0];

        try {
            DatabaseClient db = new DatabaseClient();

            int newVersion = db.bumpLearningVersion(repository,
                    "Approved via merged learning PR", "github-action");

            db.activateProposals(repository, newVersion);

            System.out.println("Learning version bumped to " + newVersion);
            System.out.println("All proposals activated for " + repository);
            System.exit(0);

        } catch (Exception e) {
            System.err.println("Failed to apply learning: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }
}
