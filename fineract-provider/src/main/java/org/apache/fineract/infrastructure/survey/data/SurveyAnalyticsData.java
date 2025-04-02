package org.apache.fineract.infrastructure.survey.data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class SurveyAnalyticsData {
    private String surveyName;
    private LocalDate startDate;
    private LocalDate endDate;
    private int totalResponses;
    private double averageScore;
    private Map<String, QuestionAnalytics> questionAnalytics;
    private List<TimeSeriesData> timeSeriesData;
    private Map<String, DemographicBreakdown> demographicBreakdowns;
    private Map<String, Double> correlations;
}

@Data
@NoArgsConstructor
@Accessors(chain = true)
class ScoreDistributionData {
    private double minScore;
    private double maxScore;
    private double medianScore;
    private double firstQuartile;
    private double thirdQuartile;
    private Map<Double, Integer> scoreFrequency;
}

@Data
@NoArgsConstructor
@Accessors(chain = true)
class ResponseTrendData {
    private Map<LocalDate, Integer> dailyResponses;
    private Map<LocalDate, Double> dailyAverageScores;
    private Map<String, Map<LocalDate, Integer>> answerTrends;
}

@Data
@NoArgsConstructor
@Accessors(chain = true)
class DemographicCorrelationData {
    private Map<String, Double> correlationWithScores;
    private Map<String, Map<String, Integer>> demographicResponsePatterns;
    private Map<String, Double> demographicAverageScores;
}

@Data
@NoArgsConstructor
@Accessors(chain = true)
class QuestionInsightData {
    private double responseRate;
    private double standardDeviation;
    private Map<String, Double> answerCorrelations;
    private String mostCommonAnswer;
    private String leastCommonAnswer;
    private double answerDiversityIndex;
} 