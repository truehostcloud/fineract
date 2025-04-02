package org.apache.fineract.infrastructure.survey.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.exception.PlatformServiceUnavailableException;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.survey.data.*;
import org.apache.fineract.infrastructure.survey.exception.SurveyNotFoundException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SurveyAnalyticsServiceImpl implements SurveyAnalyticsService {

    private static final String SURVEY_TABLE = "m_surveys";
    private static final String SCORECARD_TABLE = "m_survey_scorecards";
    private static final String QUESTION_TABLE = "m_survey_questions";
    private static final String RESPONSE_TABLE = "m_survey_responses";
    private static final String CLIENT_TABLE = "m_client";

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSpecificSQLGenerator sqlGenerator;

    private static final RowMapper<Map<String, Integer>> RESPONSE_DISTRIBUTION_MAPPER = (rs, rowNum) -> {
        Map<String, Integer> distribution = new HashMap<>();
        distribution.put(rs.getString("a_text"), rs.getInt("count"));
        return distribution;
    };

    private static final RowMapper<DemographicStats> DEMOGRAPHIC_STATS_MAPPER = (rs, rowNum) -> new DemographicStats()
            .setDemographicValue(rs.getString("field_value"))
            .setResponseCount(rs.getInt("response_count"))
            .setAverageScore(rs.getDouble("avg_score"));

    private static final RowMapper<TimeSeriesData> TIME_SERIES_DATA_MAPPER = (rs, rowNum) -> new TimeSeriesData()
            .setDate(rs.getDate("date").toLocalDate())
            .setResponseCount(rs.getInt("response_count"))
            .setAverageScore(rs.getDouble("avg_score"));

    @Override
    @Transactional(readOnly = true)
    public SurveyAnalyticsData getSurveyAnalytics(String surveyName, LocalDate startDate, LocalDate endDate) {
        validateInput(surveyName, startDate, endDate);
        context.authenticatedUser().validateHasDatatableReadPermission(surveyName);

        SurveyAnalyticsData analytics = new SurveyAnalyticsData()
                .setSurveyName(surveyName)
                .setStartDate(startDate)
                .setEndDate(endDate);

        try {
            // Get survey ID
            Long surveyId = getSurveyId(surveyName);
            if (surveyId == null) {
                throw new SurveyNotFoundException(surveyName);
            }

            // Get questions and their responses
            List<Map<String, Object>> questions = getQuestions(surveyId);
            Map<String, QuestionAnalytics> questionAnalytics = new HashMap<>();

            for (Map<String, Object> question : questions) {
                Long questionId = (Long) question.get("id");
                String questionText = (String) question.get("a_text");
                String questionKey = (String) question.get("a_key");

                QuestionAnalytics qa = new QuestionAnalytics()
                        .setQuestionCode(questionKey)
                        .setQuestionText(questionText);

                // Get response distribution
                Map<String, Integer> distribution = getResponseDistribution(surveyId, questionId, startDate, endDate);
                qa.setAnswerDistribution(distribution);

                // Calculate average score for this question
                double avgScore = calculateQuestionAverageScore(surveyId, questionId, startDate, endDate);
                qa.setAverageScore(avgScore);

                questionAnalytics.put(questionKey, qa);
            }

            analytics.setQuestionAnalytics(questionAnalytics);
            analytics.setTotalResponses(getTotalResponses(surveyId, startDate, endDate));
            analytics.setAverageScore(calculateOverallAverageScore(surveyId, startDate, endDate));

            return analytics;
        } catch (DataAccessException e) {
            log.error("Error retrieving survey analytics", e);
            throw new PlatformServiceUnavailableException("error.msg.survey.analytics.retrieval.failed",
                    "Failed to retrieve survey analytics for survey: " + surveyName, e);
        }
    }

    private void validateInput(String surveyName, LocalDate startDate, LocalDate endDate) {
        if (!StringUtils.hasText(surveyName)) {
            throw new IllegalArgumentException("Survey name cannot be empty");
        }
        validateDateRange(startDate, endDate);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date cannot be null");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }
    }

    private Long getSurveyId(String surveyName) {
        String sql = String.format("SELECT id FROM %s WHERE a_name = ?",
                sqlGenerator.escape(SURVEY_TABLE));
        return jdbcTemplate.queryForObject(sql, Long.class, surveyName);
    }

    private List<Map<String, Object>> getQuestions(Long surveyId) {
        String sql = String.format("SELECT id, a_key, a_text FROM %s WHERE survey_id = ? ORDER BY sequence_no",
                sqlGenerator.escape(QUESTION_TABLE));
        return jdbcTemplate.queryForList(sql, surveyId);
    }

    private Map<String, Integer> getResponseDistribution(Long surveyId, Long questionId, LocalDate startDate, LocalDate endDate) {
        String sql = String.format("""
                        SELECT sr.a_text, COUNT(sc.id) as count
                        FROM %s sc
                        JOIN %s sr ON sc.response_id = sr.id
                        WHERE sc.survey_id = ? AND sc.question_id = ?
                        AND sc.created_on BETWEEN ? AND ?
                        GROUP BY sr.a_text
                        """,
                sqlGenerator.escape(SCORECARD_TABLE),
                sqlGenerator.escape(RESPONSE_TABLE));

        Map<String, Integer> distribution = new HashMap<>();
        jdbcTemplate.query(sql, RESPONSE_DISTRIBUTION_MAPPER, surveyId, questionId, startDate, endDate)
                .forEach(distribution::putAll);

        return distribution;
    }

    private double calculateQuestionAverageScore(Long surveyId, Long questionId, LocalDate startDate, LocalDate endDate) {
        String sql = String.format("""
                        SELECT COALESCE(AVG(sc.a_value), 0.0) as avg_score
                        FROM %s sc
                        WHERE sc.survey_id = ? AND sc.question_id = ?
                        AND sc.created_on BETWEEN ? AND ?
                        """,
                sqlGenerator.escape(SCORECARD_TABLE));
        Double result = jdbcTemplate.queryForObject(sql, Double.class, surveyId, questionId, startDate, endDate);
        return result != null ? result : 0.0;
    }

    private int getTotalResponses(Long surveyId, LocalDate startDate, LocalDate endDate) {
        String sql = String.format("""
                        SELECT COALESCE(COUNT(DISTINCT client_id), 0) 
                        FROM %s 
                        WHERE survey_id = ? 
                        AND created_on BETWEEN ? AND ?
                        """,
                sqlGenerator.escape(SCORECARD_TABLE));
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, surveyId, startDate, endDate);
        return result != null ? result : 0;
    }

    private double calculateOverallAverageScore(Long surveyId, LocalDate startDate, LocalDate endDate) {
        String sql = String.format("""
                        SELECT COALESCE(AVG(a_value), 0.0) 
                        FROM %s 
                        WHERE survey_id = ? 
                        AND created_on BETWEEN ? AND ?
                        """,
                sqlGenerator.escape(SCORECARD_TABLE));
        Double result = jdbcTemplate.queryForObject(sql, Double.class, surveyId, startDate, endDate);
        return result != null ? result : 0.0;
    }

    @Override
    @Transactional(readOnly = true)
    public SurveyAnalyticsData getSurveyAnalyticsByDemographic(String surveyName, String demographicField, LocalDate startDate, LocalDate endDate) {
        validateInput(surveyName, startDate, endDate);
        if (!StringUtils.hasText(demographicField)) {
            throw new IllegalArgumentException("Demographic field cannot be empty");
        }

        context.authenticatedUser().validateHasDatatableReadPermission(surveyName);

        SurveyAnalyticsData analytics = new SurveyAnalyticsData()
                .setSurveyName(surveyName)
                .setStartDate(startDate)
                .setEndDate(endDate);

        try {
            Long surveyId = getSurveyId(surveyName);
            if (surveyId == null) {
                throw new SurveyNotFoundException(surveyName);
            }

            // Get demographic breakdown
            Map<String, DemographicBreakdown> demographicBreakdowns = new HashMap<>();
            DemographicBreakdown breakdown = new DemographicBreakdown()
                    .setDemographicField(demographicField);

            Map<String, DemographicStats> statsByValue = new HashMap<>();

            String sql = String.format("""
                            SELECT c.field_value, COUNT(DISTINCT sc.client_id) as response_count, AVG(sc.a_value) as avg_score
                            FROM %s sc
                            JOIN %s c ON sc.client_id = c.id
                            WHERE sc.survey_id = ? AND c.field_name = ?
                            AND sc.created_on BETWEEN ? AND ?
                            GROUP BY c.field_value
                            """,
                    sqlGenerator.escape(SCORECARD_TABLE),
                    sqlGenerator.escape(CLIENT_TABLE));

            jdbcTemplate.query(sql, DEMOGRAPHIC_STATS_MAPPER, surveyId, demographicField, startDate, endDate)
                    .forEach(stats -> statsByValue.put(stats.getDemographicValue(), stats));

            breakdown.setStatsByValue(statsByValue);
            demographicBreakdowns.put(demographicField, breakdown);
            analytics.setDemographicBreakdowns(demographicBreakdowns);

            return analytics;
        } catch (DataAccessException e) {
            log.error("Error retrieving demographic analytics", e);
            throw new PlatformServiceUnavailableException("error.msg.survey.demographic.analytics.retrieval.failed",
                    "Failed to retrieve demographic analytics for survey: " + surveyName, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SurveyAnalyticsData getTimeSeriesAnalytics(String surveyName, LocalDate startDate, LocalDate endDate, String timeUnit) {
        validateInput(surveyName, startDate, endDate);
        if (!StringUtils.hasText(timeUnit)) {
            throw new IllegalArgumentException("Time unit cannot be empty");
        }

        context.authenticatedUser().validateHasDatatableReadPermission(surveyName);

        SurveyAnalyticsData analytics = new SurveyAnalyticsData()
                .setSurveyName(surveyName)
                .setStartDate(startDate)
                .setEndDate(endDate);

        try {
            Long surveyId = getSurveyId(surveyName);
            if (surveyId == null) {
                throw new SurveyNotFoundException(surveyName);
            }

            String sql = String.format("""
                            SELECT DATE(sc.created_on) as date,
                                   COUNT(DISTINCT sc.client_id) as response_count,
                                   AVG(sc.a_value) as avg_score
                            FROM %s sc
                            WHERE sc.survey_id = ? AND sc.created_on BETWEEN ? AND ?
                            GROUP BY DATE(sc.created_on)
                            ORDER BY date
                            """,
                    sqlGenerator.escape(SCORECARD_TABLE));

            List<TimeSeriesData> timeSeriesData = new ArrayList<>(jdbcTemplate.query(sql, TIME_SERIES_DATA_MAPPER, surveyId, startDate, endDate));
            analytics.setTimeSeriesData(timeSeriesData);

            return analytics;
        } catch (DataAccessException e) {
            log.error("Error retrieving time series analytics", e);
            throw new PlatformServiceUnavailableException("error.msg.survey.timeseries.analytics.retrieval.failed",
                    "Failed to retrieve time series analytics for survey: " + surveyName, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SurveyAnalyticsData getCorrelationAnalytics(String surveyName, List<String> questionCodes, LocalDate startDate, LocalDate endDate) {
        validateInput(surveyName, startDate, endDate);
        validateQuestionCodes(questionCodes);

        context.authenticatedUser().validateHasDatatableReadPermission(surveyName);

        SurveyAnalyticsData analytics = new SurveyAnalyticsData()
                .setSurveyName(surveyName)
                .setStartDate(startDate)
                .setEndDate(endDate);

        try {
            Long surveyId = getSurveyId(surveyName);
            if (surveyId == null) {
                throw new SurveyNotFoundException(surveyName);
            }

            Map<String, Double> correlations = calculateCorrelations(surveyId, questionCodes);
            analytics.setCorrelations(correlations);
            return analytics;
        } catch (DataAccessException e) {
            log.error("Error retrieving correlation analytics", e);
            throw new PlatformServiceUnavailableException("error.msg.survey.correlation.analytics.retrieval.failed",
                    "Failed to retrieve correlation analytics for survey: " + surveyName, e);
        }
    }

    private void validateQuestionCodes(List<String> questionCodes) {
        if (questionCodes == null || questionCodes.isEmpty()) {
            throw new IllegalArgumentException("Question codes cannot be empty");
        }

        // Validate each question code format
        questionCodes.forEach(code -> {
            if (!StringUtils.hasText(code) || !code.matches("^[a-zA-Z0-9_-]+$")) {
                throw new IllegalArgumentException("Invalid question code format: " + code);
            }
        });
    }

    private Map<String, Double> calculateCorrelations(Long surveyId, List<String> questionCodes) {
        Map<String, Double> correlations = new HashMap<>();

        // Get the base data for each question
        Map<String, Map<Long, Double>> questionValues = new HashMap<>();

        // Prepare the base query for each question
        String baseQuery = String.format("""
                         SELECT sc.client_id, sc.a_value\s
                         FROM %s sc\s
                         JOIN %s sq ON sc.question_id = sq.id\s
                         WHERE sc.survey_id = ? AND sq.a_key = ?
                        \s""",
                sqlGenerator.escape(SCORECARD_TABLE),
                sqlGenerator.escape(QUESTION_TABLE));

        // Collect values for each question
        for (String questionCode : questionCodes) {
            Map<Long, Double> values = new HashMap<>();
            jdbcTemplate.query(
                    baseQuery,
                    (rs, rowNum) -> {
                        values.put(rs.getLong("client_id"), rs.getDouble("a_value"));
                        return null;
                    },
                    surveyId, questionCode
            );
            questionValues.put(questionCode, values);
        }

        // Calculate correlations
        for (int i = 0; i < questionCodes.size(); i++) {
            String code1 = questionCodes.get(i);
            Map<Long, Double> values1 = questionValues.get(code1);

            for (int j = i + 1; j < questionCodes.size(); j++) {
                String code2 = questionCodes.get(j);
                Map<Long, Double> values2 = questionValues.get(code2);

                // Calculate correlation between two questions
                double correlation = calculatePearsonCorrelation(values1, values2);
                correlations.put(code1 + "_" + code2, correlation);
            }
        }

        return correlations;
    }

    private double calculatePearsonCorrelation(Map<Long, Double> values1, Map<Long, Double> values2) {
        // Get common client IDs
        Set<Long> commonClients = values1.keySet().stream()
                .filter(values2::containsKey)
                .collect(Collectors.toSet());

        if (commonClients.isEmpty()) {
            return 0.0;
        }

        // Calculate means
        double mean1 = commonClients.stream().mapToDouble(values1::get).average().orElse(0.0);
        double mean2 = commonClients.stream().mapToDouble(values2::get).average().orElse(0.0);

        // Calculate correlation
        double numerator = 0.0;
        double denominator1 = 0.0;
        double denominator2 = 0.0;

        for (Long clientId : commonClients) {
            double diff1 = values1.get(clientId) - mean1;
            double diff2 = values2.get(clientId) - mean2;

            numerator += diff1 * diff2;
            denominator1 += diff1 * diff1;
            denominator2 += diff2 * diff2;
        }

        if (denominator1 == 0.0 || denominator2 == 0.0) {
            return 0.0;
        }

        return numerator / Math.sqrt(denominator1 * denominator2);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SurveyAnalyticsData> getComparativeAnalytics(List<String> surveyNames, LocalDate startDate, LocalDate endDate) {
        if (surveyNames == null || surveyNames.isEmpty()) {
            throw new IllegalArgumentException("Survey names cannot be empty");
        }
        validateDateRange(startDate, endDate);

        return surveyNames.stream()
                .map(surveyName -> getSurveyAnalytics(surveyName, startDate, endDate))
                .collect(Collectors.toList());
    }
} 