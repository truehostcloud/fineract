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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.spm.data.ScorecardData;
import org.apache.fineract.spm.domain.Scorecard;
import org.apache.fineract.spm.domain.Survey;
import org.apache.fineract.spm.service.ScorecardReadPlatformService;
import org.apache.fineract.spm.service.ScorecardService;
import org.apache.fineract.spm.service.SpmService;
import org.apache.fineract.spm.util.ScorecardMapper;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    @Operation(summary = "Retrieve most recent survey submission", description = "Retrieves the most recent survey submission for a client to allow updates")
    public List<ScorecardData> findBySurveyAndClient(@PathParam("surveyId") @Parameter(description = "Enter surveyId") final Long surveyId,
            @PathParam("clientId") @Parameter(description = "Enter clientId") final Long clientId) {
        this.securityContext.authenticatedUser();
        this.spmService.findById(surveyId);
        this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);

        List<ScorecardData> allSubmissions = (List<ScorecardData>) this.scorecardReadPlatformService
                .retrieveScorecardBySurveyAndClient(surveyId, clientId);

        if (allSubmissions.isEmpty()) {
            return allSubmissions;
        }
        return List.of(allSubmissions.get(0));
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
    public void updateScorecard(@PathParam("surveyId") @Parameter(description = "Survey ID") final Long surveyId,
            @PathParam("clientId") @Parameter(description = "Client ID") final Long clientId,
            @Parameter(description = "Scorecard data to update") final ScorecardData scorecardData) {
        
        if (scorecardData.getClientId() != null && !scorecardData.getClientId().equals(clientId)) {
            throw new PlatformDataIntegrityException("error.msg.scorecard.clientId.mismatch", 
                "The clientId in the path does not match the clientId in the request body", "clientId", clientId);
        }

        final AppUser appUser = this.securityContext.authenticatedUser();
        final Survey survey = this.spmService.findById(surveyId);
        final Client client = this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);

        List<Scorecard> existingScorecards = this.scorecardService.findBySurveyAndClient(survey, client);
        if (existingScorecards.isEmpty()) {
            throw new PlatformDataIntegrityException("error.msg.survey.not.submitted", "Client has not submitted this survey yet",
                    "clientId", client.getId());
        }

        List<Scorecard> mostRecentSubmission = List.of(existingScorecards.get(0));

        List<Scorecard> updatedScorecards = ScorecardMapper.map(scorecardData, survey, appUser, client);

        for (Scorecard updatedScorecard : updatedScorecards) {
            for (Scorecard existingScorecard : mostRecentSubmission) {
                if (existingScorecard.getQuestion().getId().equals(updatedScorecard.getQuestion().getId())) {
                    existingScorecard.setResponse(updatedScorecard.getResponse());
                    existingScorecard.setValue(updatedScorecard.getValue());
                    existingScorecard.setCreatedOn(updatedScorecard.getCreatedOn());
                    break;
                }
            }
        }

        this.scorecardService.updateScorecard(mostRecentSubmission);
    }
}
