package org.apache.fineract.infrastructure.survey.data;

import java.time.LocalDate;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class TimeSeriesData {

    private LocalDate date;
    private int responseCount;
    private double averageScore;
    private Map<String, Double> questionAverages;

    // Enhanced analytics fields
    private Map<String, Integer> answerDistribution;
    private Map<String, Double> standardDeviations;
    private Map<String, Map<String, Integer>> demographicBreakdowns;
    private Map<String, Double> correlationTrends;
    private Map<String, Double> responseRates;
}
