package com.utm.temporal.activity;

import com.utm.temporal.db.DatabaseClient;
import com.utm.temporal.model.LearningInsights;

public class LoadInsightsActivityImpl implements LoadInsightsActivity {
    private final DatabaseClient databaseClient;

    public LoadInsightsActivityImpl(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public LearningInsights loadInsights(String repository) {
        try {
            return databaseClient.loadInsights(repository);
        } catch (Exception e) {
            // Return null on failure — review proceeds without insights
            return null;
        }
    }
}
