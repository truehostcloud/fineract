package org.apache.fineract.infrastructure.survey.service;

import java.time.LocalDate;
import org.apache.fineract.infrastructure.survey.data.SurveyResponseAnalyticsData;

public interface SurveyAnalyticsService {
    SurveyResponseAnalyticsData getSurveyResponseAnalytics(String surveyName, LocalDate startDate, LocalDate endDate);
} 