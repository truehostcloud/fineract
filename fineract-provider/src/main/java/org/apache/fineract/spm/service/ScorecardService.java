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
package org.apache.fineract.spm.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.spm.data.ScorecardData;
import org.apache.fineract.spm.data.ScorecardValue;
import org.apache.fineract.spm.domain.Scorecard;
import org.apache.fineract.spm.domain.ScorecardRepository;
import org.apache.fineract.spm.domain.Survey;
import org.apache.fineract.spm.domain.Response;
import org.apache.fineract.spm.domain.Question;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScorecardService {

    private final PlatformSecurityContext securityContext;
    private final ScorecardRepository scorecardRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ScorecardReadPlatformService scorecardReadPlatformService;

    public List<Scorecard> createScorecard(final List<Scorecard> scorecards) {
        this.securityContext.authenticatedUser();

        return this.scorecardRepository.saveAll(scorecards);
    }

    public List<Scorecard> findBySurvey(final Survey survey) {
        this.securityContext.authenticatedUser();

        return this.scorecardRepository.findBySurvey(survey);
    }

    public List<Scorecard> findBySurveyAndClient(final Survey survey, final Client client) {
        this.securityContext.authenticatedUser();

        return this.scorecardRepository.findBySurveyAndClient(survey, client);
    }

    public boolean hasClientSubmittedSurvey(final Survey survey, final Client client) {
        this.securityContext.authenticatedUser();
        List<Scorecard> existingSubmissions = this.scorecardRepository.findBySurveyAndClient(survey, client);
        return !existingSubmissions.isEmpty();
    }

    public ScorecardData updateScorecardResponses(Survey survey, Client client, AppUser appUser, ScorecardData scorecardData) {
        // Get existing scorecards for this survey and client
        List<Scorecard> existingScorecards = scorecardRepository.findBySurveyAndClient(survey, client);
        
        // Process each updated response
        for (ScorecardValue updatedValue : scorecardData.getScorecardValues()) {
            // Find the most recent scorecard for this question
            Optional<Scorecard> existingScorecard = existingScorecards.stream()
                .filter(sc -> sc.getQuestion().getId().equals(updatedValue.getQuestionId()))
                .max((sc1, sc2) -> sc1.getCreatedOn().compareTo(sc2.getCreatedOn()));
            
            if (existingScorecard.isPresent()) {
                // Update existing scorecard
                Scorecard scorecard = existingScorecard.get();
                Response response = new Response();
                response.setId(updatedValue.getResponseId());
                scorecard.setResponse(response);
                scorecard.setValue(updatedValue.getValue());
                scorecard.setCreatedOn(LocalDateTime.now());
                scorecardRepository.save(scorecard);
            } else {
                // Create new scorecard if it doesn't exist
                Scorecard newScorecard = new Scorecard();
                newScorecard.setSurvey(survey);
                Question question = new Question();
                question.setId(updatedValue.getQuestionId());
                newScorecard.setQuestion(question);
                Response response = new Response();
                response.setId(updatedValue.getResponseId());
                newScorecard.setResponse(response);
                newScorecard.setClient(client);
                newScorecard.setAppUser(appUser);
                newScorecard.setValue(updatedValue.getValue());
                newScorecard.setCreatedOn(LocalDateTime.now());
                scorecardRepository.save(newScorecard);
            }
        }
        
        // Return updated scorecard data
        return scorecardReadPlatformService.retrieveScorecardBySurveyAndClient(survey.getId(), client.getId())
            .stream()
            .findFirst()
            .orElseThrow(() -> new PlatformDataIntegrityException("error.msg.survey.update.failed", 
                "Failed to update survey responses", "surveyId", survey.getId()));
    }
}
