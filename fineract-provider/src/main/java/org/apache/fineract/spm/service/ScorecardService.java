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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
public class ScorecardService {

    private final PlatformSecurityContext securityContext;
    private final ScorecardRepository scorecardRepository;

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

    public List<Scorecard> updateScorecard(final List<Scorecard> scorecards) {
        this.securityContext.authenticatedUser();
        List<Scorecard> updatedScorecards = new ArrayList<>();
        
        // Get the survey and client from the first scorecard (they should be the same for all)
        Survey survey = scorecards.get(0).getSurvey();
        Client client = scorecards.get(0).getClient();
        
        // Get existing scorecards for this survey and client
        List<Scorecard> existingScorecards = this.scorecardRepository.findBySurveyAndClient(survey, client);
        
        // Find the latest submission timestamp
        LocalDateTime latestSubmissionTime = existingScorecards.stream()
                .map(Scorecard::getCreatedOn)
                .max(LocalDateTime::compareTo)
                .orElseThrow(() -> new PlatformDataIntegrityException("error.msg.survey.no.timestamp",
                        "No timestamp found for survey submission", "clientId", client.getId()));
        
        // Get only the scorecards from the latest submission
        List<Scorecard> latestSubmissionScorecards = existingScorecards.stream()
                .filter(sc -> sc.getCreatedOn().equals(latestSubmissionTime))
                .collect(Collectors.toList());
        
        // Create a map of existing scorecards by question ID
        Map<Long, Scorecard> existingScorecardsByQuestion = latestSubmissionScorecards.stream()
                .collect(Collectors.toMap(sc -> sc.getQuestion().getId(), sc -> sc));
        
        // Update the scorecards
        for (Scorecard newScorecard : scorecards) {
            Scorecard existingScorecard = existingScorecardsByQuestion.get(newScorecard.getQuestion().getId());
            if (existingScorecard != null) {
                existingScorecard.setResponse(newScorecard.getResponse());
                existingScorecard.setValue(newScorecard.getValue());
                updatedScorecards.add(existingScorecard);
            }
        }
        
        // Save all updates in a single transaction
        return this.scorecardRepository.saveAll(updatedScorecards);
    }
}
