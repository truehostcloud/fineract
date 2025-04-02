package org.apache.fineract.infrastructure.survey.service;

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.infrastructure.survey.data.SurveyAnalyticsData;

public interface SurveyAnalyticsService {
    
    SurveyAnalyticsData getSurveyAnalytics(String surveyName, LocalDate startDate, LocalDate endDate);
    
    SurveyAnalyticsData getSurveyAnalyticsByDemographic(String surveyName, String demographicField, LocalDate startDate, LocalDate endDate);
    
    List<SurveyAnalyticsData> getComparativeAnalytics(List<String> surveyNames, LocalDate startDate, LocalDate endDate);
    
    SurveyAnalyticsData getTimeSeriesAnalytics(String surveyName, LocalDate startDate, LocalDate endDate, String timeUnit);
    
    SurveyAnalyticsData getCorrelationAnalytics(String surveyName, List<String> questionCodes, LocalDate startDate, LocalDate endDate);
} 