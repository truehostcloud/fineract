package org.apache.fineract.infrastructure.survey.data;

import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class DemographicBreakdown {

    private String demographicField;
    private Map<String, DemographicStats> statsByValue;

    // Enhanced analytics fields
    private Map<String, Double> correlationWithScores;
    private Map<String, Map<String, Integer>> demographicResponsePatterns;
    private Map<String, Double> demographicAverageScores;
    private Map<String, Map<String, Double>> questionAveragesByValue;
    private Map<String, Double> standardDeviationsByValue;
}
