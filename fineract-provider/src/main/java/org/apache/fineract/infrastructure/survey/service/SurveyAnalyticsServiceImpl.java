package org.apache.fineract.infrastructure.survey.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.survey.data.SurveyResponseAnalyticsData;
import org.apache.fineract.spm.exception.SurveyNotFoundException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SurveyAnalyticsServiceImpl implements SurveyAnalyticsService {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    
    private static final String SURVEY_AND_QUESTIONS_QUERY = """
            SELECT s.id as survey_id, s.a_name as survey_name, s.description as survey_description,
                   s.country_code, s.valid_from, s.valid_to,
                   q.id as question_id, q.a_text as question_text, q.description as question_description,
                   q.a_key as `key`, q.sequence_no
            FROM m_surveys s
            JOIN m_survey_questions q ON s.id = q.survey_id
            WHERE s.a_name = :surveyName
            ORDER BY q.sequence_no
            """;

    
    private static final String RESPONSES_QUERY = """
            SELECT r.id as response_id, r.question_id, r.a_text as response_text, r.sequence_no
            FROM m_survey_responses r
            WHERE r.question_id IN (:questionIds)
            ORDER BY r.sequence_no
            """;

    
    private static final String RESPONSE_STATS_QUERY = """
             WITH response_counts AS (
                 SELECT\s
                     sc.question_id,
                     sc.response_id,
                     COUNT(DISTINCT sc.client_id) as unique_clients,
                     COUNT(*) as total_responses
                 FROM m_survey_scorecards sc
                 WHERE sc.survey_id = :surveyId
                     AND (:startDate IS NULL OR sc.created_on >= :startDate)
                     AND (:endDate IS NULL OR sc.created_on <= :endDate)
                 GROUP BY sc.question_id, sc.response_id
             ),
             question_totals AS (
                 SELECT\s
                     question_id,
                     SUM(total_responses) as question_total
                 FROM response_counts
                 GROUP BY question_id
             )
             SELECT\s
                 rc.question_id,
                 rc.response_id,
                 rc.unique_clients,
                 rc.total_responses,
                 ROUND((rc.total_responses * 100.0) / qt.question_total, 2) as percentage
             FROM response_counts rc
             JOIN question_totals qt ON rc.question_id = qt.question_id
            \s""";

    
    private static final String NULL_RESPONSES_QUERY = """
            SELECT\s
                question_id,
                COUNT(DISTINCT client_id) as unique_clients,
                COUNT(*) as total_responses
            FROM m_survey_scorecards
            WHERE survey_id = :surveyId
                AND response_id IS NULL
                AND (:startDate IS NULL OR created_on >= :startDate)
                AND (:endDate IS NULL OR created_on <= :endDate)
            GROUP BY question_id
           \s""";

    
    private static final String TOTAL_UNIQUE_CLIENTS_QUERY = """
            SELECT COUNT(DISTINCT client_id) as total
            FROM m_survey_scorecards
            WHERE survey_id = :surveyId
                AND (:startDate IS NULL OR created_on >= :startDate)
                AND (:endDate IS NULL OR created_on <= :endDate)
            """;

    @Override
    @Transactional(readOnly = true)
    public SurveyResponseAnalyticsData getSurveyResponseAnalytics(String surveyName, LocalDate startDate, LocalDate endDate) {
        if (surveyName == null || surveyName.isBlank()) {
            throw new IllegalArgumentException("Survey name cannot be null or empty");
        }

        
        final SurveyResponseAnalyticsData analytics = new SurveyResponseAnalyticsData()
                .setSurveyName(surveyName)
                .setStartDate(startDate)
                .setEndDate(endDate);

        try {
            
            MapSqlParameterSource surveyParams = new MapSqlParameterSource("surveyName", surveyName);
            List<Map<String, Object>> surveyAndQuestions = namedParameterJdbcTemplate.queryForList(
                    SURVEY_AND_QUESTIONS_QUERY, surveyParams);

            if (CollectionUtils.isEmpty(surveyAndQuestions)) {
                throw new SurveyNotFoundException(surveyName);
            }

            
            Map<String, Object> firstRow = surveyAndQuestions.get(0);
            Long surveyId = getLongValue(firstRow);

            
            validateDates(startDate, endDate, firstRow);

            
            MapSqlParameterSource totalParams = new MapSqlParameterSource()
                    .addValue("surveyId", surveyId)
                    .addValue("startDate", startDate != null ? Date.valueOf(startDate) : null)
                    .addValue("endDate", endDate != null ? Date.valueOf(endDate) : null);

            Integer totalClients = queryForIntSafely(totalParams);
            analytics.setTotalResponses(totalClients);

            
            List<String> questionIds = surveyAndQuestions.stream()
                    .map(row -> String.valueOf(row.get("question_id")))
                    .collect(Collectors.toList());

            
            MapSqlParameterSource responsesParams = new MapSqlParameterSource("questionIds", questionIds);
            List<Map<String, Object>> responses = namedParameterJdbcTemplate.queryForList(
                    RESPONSES_QUERY, responsesParams);

            
            Map<String, List<Map<String, Object>>> responsesByQuestion = responses.stream()
                    .collect(Collectors.groupingBy(row -> String.valueOf(row.get("question_id"))));

            
            MapSqlParameterSource statsParams = new MapSqlParameterSource()
                    .addValue("surveyId", surveyId)
                    .addValue("startDate", startDate != null ? Date.valueOf(startDate) : null)
                    .addValue("endDate", endDate != null ? Date.valueOf(endDate) : null);

            List<Map<String, Object>> responseStats = namedParameterJdbcTemplate.queryForList(
                    RESPONSE_STATS_QUERY, statsParams);

            
            List<Map<String, Object>> nullResponses = namedParameterJdbcTemplate.queryForList(
                    NULL_RESPONSES_QUERY, statsParams);

            
            Map<String, Map<String, Map<String, Object>>> statsByQuestionAndResponse = new HashMap<>();

            for (Map<String, Object> stat : responseStats) {
                String qId = String.valueOf(stat.get("question_id"));
                String rId = stat.get("response_id") != null ? String.valueOf(stat.get("response_id")) : "null";

                statsByQuestionAndResponse
                        .computeIfAbsent(qId, k -> new HashMap<>())
                        .put(rId, stat);
            }

            
            for (Map<String, Object> nullStat : nullResponses) {
                String qId = String.valueOf(nullStat.get("question_id"));

                statsByQuestionAndResponse
                        .computeIfAbsent(qId, k -> new HashMap<>())
                        .put("null", nullStat);
            }

            
            List<SurveyResponseAnalyticsData.QuestionAnalytics> questionAnalytics = new ArrayList<>();

            for (Map<String, Object> question : surveyAndQuestions) {
                String questionId = String.valueOf(question.get("question_id"));
                String questionText = (String) question.get("question_text");
                String questionKey = (String) question.get("key");

                SurveyResponseAnalyticsData.QuestionAnalytics qAnalytics = new SurveyResponseAnalyticsData.QuestionAnalytics()
                        .setQuestionCode(questionKey)
                        .setQuestionText(questionText);

                List<SurveyResponseAnalyticsData.QuestionAnalytics.ChoiceAnalytics> choices = new ArrayList<>();

                
                List<Map<String, Object>> questionResponses = responsesByQuestion.getOrDefault(questionId, Collections.emptyList());
                Map<String, Map<String, Object>> questionStats = statsByQuestionAndResponse.getOrDefault(questionId, Collections.emptyMap());

                for (Map<String, Object> response : questionResponses) {
                    String responseId = String.valueOf(response.get("response_id"));
                    String responseText = (String) response.get("response_text");

                    Map<String, Object> stats = questionStats.get(responseId);
                    int count = stats != null ? ((Number) stats.get("total_responses")).intValue() : 0;
                    double percentage = stats != null ? ((Number) stats.get("percentage")).doubleValue() : 0.0;

                    choices.add(new SurveyResponseAnalyticsData.QuestionAnalytics.ChoiceAnalytics()
                            .setChoiceText(responseText)
                            .setCount(count)
                            .setPercentage(percentage));
                }

                
                Map<String, Object> nullStats = questionStats.get("null");
                if (nullStats != null) {
                    int nullCount = ((Number) nullStats.get("total_responses")).intValue();

                    
                    double nullPercentage;
                    if (nullStats.containsKey("percentage")) {
                        nullPercentage = ((Number) nullStats.get("percentage")).doubleValue();
                    } else {
                        
                        int totalForQuestion = questionResponses.stream()
                                .mapToInt(r -> {
                                    String rId = String.valueOf(r.get("response_id"));
                                    Map<String, Object> stats = questionStats.get(rId);
                                    return stats != null ? ((Number) stats.get("total_responses")).intValue() : 0;
                                })
                                .sum() + nullCount;

                        nullPercentage = totalForQuestion > 0 ? (double) nullCount / totalForQuestion * 100 : 0.0;
                    }

                    choices.add(new SurveyResponseAnalyticsData.QuestionAnalytics.ChoiceAnalytics()
                            .setChoiceText("NULL")
                            .setCount(nullCount)
                            .setPercentage(nullPercentage));
                }

                qAnalytics.setChoices(choices);
                questionAnalytics.add(qAnalytics);
            }

            analytics.setQuestions(questionAnalytics);
            return analytics;

        } catch (EmptyResultDataAccessException e) {
            log.error("Survey not found: {}", surveyName, e);
            throw new SurveyNotFoundException(surveyName);
        } catch (DataAccessException e) {
            log.error("Database error while fetching survey analytics for survey: {}", surveyName, e);
            throw new RuntimeException("Error fetching survey analytics: " + e.getMessage(), e);
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate, Map<String, Object> surveyData) {
        if (startDate != null && endDate != null) {
            if (startDate.isAfter(endDate)) {
                throw new IllegalArgumentException("Start date cannot be after end date");
            }

            LocalDate validFrom = getLocalDateValue(surveyData, "valid_from");
            LocalDate validTo = getLocalDateValue(surveyData, "valid_to");

            if (validFrom != null && startDate.isBefore(validFrom)) {
                throw new IllegalArgumentException("Start date cannot be before survey validity period");
            }

            if (validTo != null && endDate.isAfter(validTo)) {
                throw new IllegalArgumentException("End date cannot be after survey validity period");
            }
        }
    }

    private Integer queryForIntSafely(MapSqlParameterSource params) {
        try {
            return namedParameterJdbcTemplate.queryForObject(SurveyAnalyticsServiceImpl.TOTAL_UNIQUE_CLIENTS_QUERY, params, Integer.class);
        } catch (DataAccessException e) {
            log.warn("Error querying for int with SQL: {}, params: {}", SurveyAnalyticsServiceImpl.TOTAL_UNIQUE_CLIENTS_QUERY, params, e);
            return 0;
        }
    }

    private Long getLongValue(Map<String, Object> data) {
        if (data == null || !data.containsKey("survey_id") || data.get("survey_id") == null) {
            return null;
        }
        Object value = data.get("survey_id");
        return value instanceof Long ? (Long) value : Long.valueOf(value.toString());
    }

    private LocalDate getLocalDateValue(Map<String, Object> data, String key) {
        if (data == null || !data.containsKey(key) || data.get(key) == null) {
            return null;
        }
        Object value = data.get(key);
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        return null;
    }
}