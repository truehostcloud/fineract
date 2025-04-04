package org.apache.fineract.infrastructure.survey.data;

import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class QuestionAnalyticsData {

    private String questionCode;
    private String questionText;
    private Map<String, Integer> answerDistribution;
    private double averageScore;
    private double standardDeviation;
    private int totalResponses;
    private double responseRate;
    private Map<String, Double> answerCorrelations;
    private String mostCommonAnswer;
    private String leastCommonAnswer;
    private double answerDiversityIndex;
}
