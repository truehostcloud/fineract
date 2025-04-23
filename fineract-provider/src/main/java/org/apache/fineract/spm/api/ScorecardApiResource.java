/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.spm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.spm.data.ScorecardData;
import org.apache.fineract.spm.data.ScorecardValue;
import org.apache.fineract.spm.domain.Response;
import org.apache.fineract.spm.domain.Scorecard;
import org.apache.fineract.spm.domain.Survey;
import org.apache.fineract.spm.service.ScorecardReadPlatformService;
import org.apache.fineract.spm.service.ScorecardService;
import org.apache.fineract.spm.service.SpmService;
import org.apache.fineract.spm.util.ScorecardMapper;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/v1/surveys/scorecards")
@Component
@Tag(name = "Score Card", description = "")
@RequiredArgsConstructor
public class ScorecardApiResource {

    private final PlatformSecurityContext securityContext;
    private final SpmService spmService;
    private final ScorecardService scorecardService;
    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final ScorecardReadPlatformService scorecardReadPlatformService;
    private static final Logger log = LoggerFactory.getLogger(ScorecardApiResource.class);

    @GET
    @Path("{surveyId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Transactional
    @Operation(summary = "List all Scorecard entries", description = "List all Scorecard entries for a survey.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Scorecard.class)))) })
    public List<ScorecardData> findBySurvey(@PathParam("surveyId") @Parameter(description = "Enter surveyId") final Long surveyId) {
        this.securityContext.authenticatedUser();
        this.spmService.findById(surveyId);
        return (List<ScorecardData>) this.scorecardReadPlatformService.retrieveScorecardBySurvey(surveyId);
    }

    @POST
    @Path("{surveyId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Transactional
    @Operation(summary = "Create a Scorecard entry", description = "Add a new entry to a survey.\n" + "\n" + "Mandatory Fields\n"
            + "clientId, createdOn, questionId, responseId, staffId")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public void createScorecard(@PathParam("surveyId") @Parameter(description = "Enter surveyId") final Long surveyId,
            @Parameter(description = "scorecardData") final ScorecardData scorecardData) {
        final AppUser appUser = this.securityContext.authenticatedUser();
        final Survey survey = this.spmService.findById(surveyId);
        final Client client = this.clientRepositoryWrapper.findOneWithNotFoundDetection(scorecardData.getClientId());

        if (this.scorecardService.hasClientSubmittedSurvey(survey, client)) {
            throw new PlatformDataIntegrityException("error.msg.survey.already.submitted", "Client has already submitted this survey",
                    "clientId", client.getId());
        }

        this.scorecardService.createScorecard(ScorecardMapper.map(scorecardData, survey, appUser, client));
    }

    @GET
    @Path("{surveyId}/clients/{clientId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Transactional
    public List<ScorecardData> findBySurveyAndClient(@PathParam("surveyId") @Parameter(description = "Enter surveyId") final Long surveyId,
            @PathParam("clientId") @Parameter(description = "Enter clientId") final Long clientId) {
        this.securityContext.authenticatedUser();
        this.spmService.findById(surveyId);
        this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);
        return (List<ScorecardData>) this.scorecardReadPlatformService.retrieveScorecardBySurveyAndClient(surveyId, clientId);

    }

    @GET
    @Path("clients/{clientId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Transactional
    public List<ScorecardData> findByClient(@PathParam("clientId") final Long clientId) {
        this.securityContext.authenticatedUser();
        this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);
        return (List<ScorecardData>) this.scorecardReadPlatformService.retrieveScorecardByClient(clientId);
    }

    @PUT
    @Path("{surveyId}/clients/{clientId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Transactional
    @Operation(summary = "Update a Scorecard entry", description = "Updates the most recent survey submission for a client.", responses = {
            @ApiResponse(responseCode = "200", description = "Scorecard updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Survey or client not found"),
            @ApiResponse(responseCode = "409", description = "Survey not submitted yet") })
            public List<ScorecardData> updateScorecard(@PathParam("surveyId") @Parameter(description = "Survey ID") final Long surveyId,
            @PathParam("clientId") @Parameter(description = "Client ID") final Long clientId,
            @Parameter(description = "Scorecard data to update") final ScorecardData scorecardData) {
        this.securityContext.authenticatedUser();
        final Survey survey = this.spmService.findById(surveyId);
        final Client client = this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);

        List<Scorecard> existingScorecards = this.scorecardService.findBySurveyAndClient(survey, client);
        if (existingScorecards.isEmpty()) {
            throw new PlatformDataIntegrityException("error.msg.survey.not.submitted", "Client has not submitted this survey yet",
                    "clientId", client.getId());
        }

        LocalDateTime latestSubmissionTime = existingScorecards.stream().map(Scorecard::getCreatedOn).max(LocalDateTime::compareTo)
                .orElseThrow(() -> new PlatformDataIntegrityException("error.msg.survey.no.timestamp",
                        "No timestamp found for survey submission", "clientId", client.getId()));

        List<Scorecard> latestSubmissionScorecards = existingScorecards.stream()
                .filter(sc -> sc.getCreatedOn().equals(latestSubmissionTime)).collect(Collectors.toList());

        Map<Long, Scorecard> latestScorecardsByQuestion = latestSubmissionScorecards.stream()
                .collect(Collectors.toMap(sc -> sc.getQuestion().getId(), sc -> sc));

        List<Scorecard> updatedScorecards = new ArrayList<>();
        for (ScorecardValue newValue : scorecardData.getScorecardValues()) {
            Scorecard existingScorecard = latestScorecardsByQuestion.get(newValue.getQuestionId());
            if (existingScorecard != null) {

                existingScorecard.setResponse(this.findResponseById(survey, newValue.getResponseId()));
                existingScorecard.setValue(newValue.getValue());
                updatedScorecards.add(existingScorecard);
            }
        }

        this.scorecardService.updateScorecard(updatedScorecards);

        return (List<ScorecardData>) this.scorecardReadPlatformService.retrieveScorecardBySurveyAndClient(surveyId, clientId);
    }
  
    private Response findResponseById(Survey survey, Long responseId) {
        return survey.getQuestions().stream().flatMap(q -> q.getResponses().stream()).filter(r -> r.getId().equals(responseId)).findFirst()
                .orElseThrow(() -> new PlatformDataIntegrityException("error.msg.survey.response.not.found", "Response not found",
                        "responseId", responseId));
    }

    @GET
    @Path("clients/{clientId}/surveys")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Transactional
    @Operation(summary = "View client survey responses", description = "Retrieves the latest survey submissions for a client")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScorecardData.class)))) })
    public List<ScorecardData> viewClientResponses(@PathParam("clientId") @Parameter(description = "Client ID") final Long clientId) {
        this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);

        Collection<ScorecardData> allSubmissions = this.scorecardReadPlatformService.retrieveScorecardByClient(clientId);

        Map<Long, List<ScorecardData>> submissionsBySurvey = allSubmissions.stream()
                .collect(Collectors.groupingBy(ScorecardData::getSurveyId));

        List<ScorecardData> result = new ArrayList<>();

        for (Map.Entry<Long, List<ScorecardData>> entry : submissionsBySurvey.entrySet()) {
            List<ScorecardData> surveySubmissions = entry.getValue();

            // Flatten all scorecard values from all submissions
            List<ScorecardValue> allValues = surveySubmissions.stream()
                    .flatMap(submission -> submission.getScorecardValues().stream())
                    .collect(Collectors.toList());

            // Group values by questionId and get the latest value for each question
            Map<Long, ScorecardValue> latestValuesByQuestion = allValues.stream()
                    .collect(Collectors.toMap(
                        ScorecardValue::getQuestionId,
                        value -> value,
                        (v1, v2) -> v1.getCreatedOn().isAfter(v2.getCreatedOn()) ? v1 : v2
                    ));

            // Create a new ScorecardData with the latest values for each question
            if (!latestValuesByQuestion.isEmpty()) {
                ScorecardData completeSubmission = new ScorecardData();
                completeSubmission.setId(surveySubmissions.get(0).getId());
                completeSubmission.setUserId(surveySubmissions.get(0).getUserId());
                completeSubmission.setUsername(surveySubmissions.get(0).getUsername());
                completeSubmission.setClientId(surveySubmissions.get(0).getClientId());
                completeSubmission.setSurveyId(surveySubmissions.get(0).getSurveyId());
                completeSubmission.setSurveyName(surveySubmissions.get(0).getSurveyName());
                completeSubmission.setScorecardValues(new ArrayList<>(latestValuesByQuestion.values()));
                
                result.add(completeSubmission);
            }
        }

        return result;
    }
    
    @GET
    @Path("clients/{clientId}/surveys/debug")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Transactional
    @Operation(summary = "Debug endpoint for client survey responses", description = "Returns all survey submissions for a client without filtering")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScorecardData.class)))) })
    public List<ScorecardData> debugClientResponses(@PathParam("clientId") @Parameter(description = "Client ID") final Long clientId) {
        this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);

        // Get all submissions without any filtering using the debug method
        Collection<ScorecardData> allSubmissions = this.scorecardReadPlatformService.retrieveScorecardByClientDebug(clientId);
        
        // Log the raw data for debugging
        log.error("Raw submissions for client {}: {}", clientId, allSubmissions);
        
        // Return all submissions as is
        return new ArrayList<>(allSubmissions);
    }
}
