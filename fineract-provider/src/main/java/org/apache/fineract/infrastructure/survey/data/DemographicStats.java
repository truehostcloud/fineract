package org.apache.fineract.infrastructure.survey.data;

import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class DemographicStats {

    private String demographicValue;
    private int responseCount;
    private double averageScore;
    private Map<String, Double> questionAverages;
}
