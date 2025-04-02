package org.apache.fineract.infrastructure.survey.data;

import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class QuestionAnalytics {
    private String questionCode;
    private String questionText;
    private Map<String, Integer> answerDistribution;
    private double averageScore;
    private double standardDeviation;
    private Map<String, Double> correlationsWithOtherQuestions;
    
    // Enhanced analytics fields
    private int totalResponses;
    private double responseRate;
    private String mostCommonAnswer;
    private String leastCommonAnswer;
    private double answerDiversityIndex;
    private double minScore;
    private double maxScore;
    private double medianScore;
    private double firstQuartile;
    private double thirdQuartile;
    private Map<Double, Integer> scoreFrequency;
} 