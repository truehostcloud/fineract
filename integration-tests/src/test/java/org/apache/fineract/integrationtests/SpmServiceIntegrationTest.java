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
package org.apache.fineract.integrationtests;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.integrationtests.client.IntegrationTest;
import org.apache.fineract.integrationtests.common.SurveyHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpmServiceIntegrationTest extends IntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(SpmServiceIntegrationTest.class);
    private SurveyHelper surveyHelper;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.surveyHelper = new SurveyHelper(fineractClient());
    }

    @Test
    @Order(1)
    void testCreateSurvey() {
        LOG.info("-------------------------Creating Survey---------------------------");
        String surveyName = "Test Survey " + System.currentTimeMillis();
        String description = "Test Survey Description";
        LocalDate validFrom = Utils.getLocalDateOfTenant();
        LocalDate validTo = validFrom.plusYears(100);
        List<String> questions = new ArrayList<>();
        questions.add("Question 1");
        questions.add("Question 2");

        Long surveyId = surveyHelper.createSurvey(surveyName, description, validFrom, validTo, questions);
        assertThat(surveyId).isNotNull();

        var survey = surveyHelper.retrieveSurvey(surveyId);
        assertThat(survey).isNotNull();
        assertThat(surveyHelper.getSurveyName(survey)).isEqualTo(surveyName);
        assertThat(surveyHelper.getSurveyDescription(survey)).isEqualTo(description);
        assertThat(surveyHelper.getSurveyValidFrom(survey)).isEqualTo(validFrom);
        assertThat(surveyHelper.getSurveyValidTo(survey)).isEqualTo(validTo);
        assertThat(surveyHelper.getSurveyQuestionsCount(survey)).isEqualTo(2);
    }

    @Test
    @Order(2)
    void testCreateSurveyWithInvalidData() {
        LOG.info("-------------------------Testing Survey Creation with Invalid Data---------------------------");
        String surveyName = "Test Survey " + System.currentTimeMillis();
        String description = "Test Survey Description";
        LocalDate validFrom = Utils.getLocalDateOfTenant();
        LocalDate validTo = validFrom.plusYears(100);
        List<String> questions = new ArrayList<>();

        assertThatThrownBy(() -> {
            surveyHelper.createSurvey(surveyName, description, validFrom, validTo, questions);
        }).isInstanceOf(RuntimeException.class);
    }
}
