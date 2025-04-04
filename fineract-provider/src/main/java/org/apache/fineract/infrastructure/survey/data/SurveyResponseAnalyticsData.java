package org.apache.fineract.infrastructure.survey.data;

import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SurveyResponseAnalyticsData {
    private String surveyName;
    private LocalDate startDate;
    private LocalDate endDate;
    private int totalResponses;
    private List<QuestionAnalytics> questions;

    @Data
    @Accessors(chain = true)
    public static class QuestionAnalytics {
        private String questionCode;
        private String questionText;
        private List<ChoiceAnalytics> choices;

        @Data
        @Accessors(chain = true)
        public static class ChoiceAnalytics {
            private String choiceText;
            private int count;
            private double percentage;
        }
    }
}
