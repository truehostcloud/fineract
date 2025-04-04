package org.apache.fineract.infrastructure.survey.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.survey.data.SurveyResponseAnalyticsData;
import org.apache.fineract.infrastructure.survey.service.SurveyAnalyticsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Path("/v1/surveys/analytics")
@Tag(name = "Survey Analytics", description = "Survey analytics and statistics endpoints")
@Component
@RequiredArgsConstructor
public class SurveyAnalyticsApiResource {

    private final SurveyAnalyticsService analyticsService;
    private final DefaultToApiJsonSerializer<SurveyResponseAnalyticsData> responseAnalyticsSerializer;

    @GET
    @Path("/{surveyName}/responses")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Get survey response analytics", description = "Retrieves response distribution analytics for a specific survey")
    public String getSurveyResponseAnalytics(@PathParam("surveyName") @Parameter(description = "Survey name") final String surveyName,
            @QueryParam("startDate") @Parameter(description = "Start date (yyyy-MM-dd)") final String startDate,
            @QueryParam("endDate") @Parameter(description = "End date (yyyy-MM-dd)") final String endDate) {

        if (!StringUtils.hasText(surveyName)) {
            throw new IllegalArgumentException("Survey name must not be empty");
        }

        LocalDate start = null;
        LocalDate end = null;

        try {
            if (StringUtils.hasText(startDate)) {
                start = LocalDate.parse(startDate);
            }
            if (StringUtils.hasText(endDate)) {
                end = LocalDate.parse(endDate);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date format. Please use yyyy-MM-dd");
        }

        final SurveyResponseAnalyticsData analytics = analyticsService.getSurveyResponseAnalytics(surveyName, start, end);
        return responseAnalyticsSerializer.serialize(analytics);
    }
}
