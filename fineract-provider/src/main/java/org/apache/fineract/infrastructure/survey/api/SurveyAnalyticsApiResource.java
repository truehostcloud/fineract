package org.apache.fineract.infrastructure.survey.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.survey.data.SurveyAnalyticsData;
import org.apache.fineract.infrastructure.survey.service.SurveyAnalyticsService;
import org.springframework.stereotype.Component;

@Path("/v1/surveys/analytics")
@Component
@Tag(name = "Survey Analytics", description = "Survey analytics and statistics endpoints")
@RequiredArgsConstructor
public class SurveyAnalyticsApiResource {

    private final SurveyAnalyticsService analyticsService;
    private final DefaultToApiJsonSerializer<SurveyAnalyticsData> toApiJsonSerializer;

    @GET
    @Path("/{surveyName}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Get survey analytics", description = "Retrieves analytics for a specific survey")
    public String getSurveyAnalytics(
            @PathParam("surveyName") @Parameter(description = "Survey name") String surveyName,
            @QueryParam("startDate") @Parameter(description = "Start date (yyyy-MM-dd)") String startDate,
            @QueryParam("endDate") @Parameter(description = "End date (yyyy-MM-dd)") String endDate) {
        
        LocalDate[] dates = parseDates(startDate, endDate);
        SurveyAnalyticsData analytics = analyticsService.getSurveyAnalytics(surveyName, dates[0], dates[1]);
        return toApiJsonSerializer.serialize(analytics);
    }

    @GET
    @Path("/{surveyName}/demographic")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Get demographic analytics", description = "Retrieves analytics broken down by demographic field")
    public String getDemographicAnalytics(
            @PathParam("surveyName") @Parameter(description = "Survey name") String surveyName,
            @QueryParam("demographicField") @Parameter(description = "Demographic field name") String demographicField,
            @QueryParam("startDate") @Parameter(description = "Start date (yyyy-MM-dd)") String startDate,
            @QueryParam("endDate") @Parameter(description = "End date (yyyy-MM-dd)") String endDate) {
        
        LocalDate[] dates = parseDates(startDate, endDate);
        SurveyAnalyticsData analytics = analyticsService.getSurveyAnalyticsByDemographic(surveyName, demographicField, dates[0], dates[1]);
        return toApiJsonSerializer.serialize(analytics);
    }

    @GET
    @Path("/{surveyName}/timeseries")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Get time series analytics", description = "Retrieves analytics over time")
    public String getTimeSeriesAnalytics(
            @PathParam("surveyName") @Parameter(description = "Survey name") String surveyName,
            @QueryParam("startDate") @Parameter(description = "Start date (yyyy-MM-dd)") String startDate,
            @QueryParam("endDate") @Parameter(description = "End date (yyyy-MM-dd)") String endDate,
            @QueryParam("timeUnit") @Parameter(description = "Time unit (day, week, month)") @DefaultValue("day") String timeUnit) {
        
        LocalDate[] dates = parseDates(startDate, endDate);
        SurveyAnalyticsData analytics = analyticsService.getTimeSeriesAnalytics(surveyName, dates[0], dates[1], timeUnit);
        return toApiJsonSerializer.serialize(analytics);
    }

    @GET
    @Path("/{surveyName}/correlations")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Get correlation analytics", description = "Retrieves correlations between questions")
    public String getCorrelationAnalytics(
            @PathParam("surveyName") @Parameter(description = "Survey name") String surveyName,
            @QueryParam("questionCodes") @Parameter(description = "Question codes to correlate") List<String> questionCodes,
            @QueryParam("startDate") @Parameter(description = "Start date (yyyy-MM-dd)") String startDate,
            @QueryParam("endDate") @Parameter(description = "End date (yyyy-MM-dd)") String endDate) {
        
        LocalDate[] dates = parseDates(startDate, endDate);
        SurveyAnalyticsData analytics = analyticsService.getCorrelationAnalytics(surveyName, questionCodes, dates[0], dates[1]);
        return toApiJsonSerializer.serialize(analytics);
    }

    @GET
    @Path("/comparative")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Get comparative analytics", description = "Retrieves analytics comparing multiple surveys")
    public String getComparativeAnalytics(
            @QueryParam("surveyNames") @Parameter(description = "Survey names to compare") List<String> surveyNames,
            @QueryParam("startDate") @Parameter(description = "Start date (yyyy-MM-dd)") String startDate,
            @QueryParam("endDate") @Parameter(description = "End date (yyyy-MM-dd)") String endDate) {
        
        LocalDate[] dates = parseDates(startDate, endDate);
        List<SurveyAnalyticsData> analytics = analyticsService.getComparativeAnalytics(surveyNames, dates[0], dates[1]);
        return toApiJsonSerializer.serialize(analytics);
    }

    private LocalDate[] parseDates(String startDate, String endDate) {
        LocalDate now = LocalDate.now();
        LocalDate start;
        LocalDate end;

        if (startDate != null && !startDate.trim().isEmpty()) {
            start = LocalDate.parse(startDate);
        } else {
            start = LocalDate.of(now.getYear(), 1, 1);
        }

        if (endDate != null && !endDate.trim().isEmpty()) {
            end = LocalDate.parse(endDate);
        } else {
            end = LocalDate.of(now.getYear(), 12, 31);
        }

        return new LocalDate[]{start, end};
    }
} 