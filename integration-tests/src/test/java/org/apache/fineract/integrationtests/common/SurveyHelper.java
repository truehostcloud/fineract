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
package org.apache.fineract.integrationtests.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.client.models.QuestionData;
import org.apache.fineract.client.models.ResponseData;
import org.apache.fineract.client.models.SurveyData;
import org.apache.fineract.client.services.SpmSurveysApi;
import org.apache.fineract.client.util.FineractClient;

public class SurveyHelper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final SpmSurveysApi surveysApi;

    public SurveyHelper(final FineractClient fineractClient) {
        this.surveysApi = fineractClient.surveys;
    }

    public Long createSurvey(String name, String description, LocalDate validFrom, LocalDate validTo, List<String> questions) {
        try {

            SurveyData surveyData = new SurveyData().name(name).description(description).validFrom(validFrom).validTo(validTo)
                    .countryCode("GB").key("SURVEY_" + System.currentTimeMillis());

            List<QuestionData> questionDataList = new ArrayList<>();
            for (int i = 0; i < questions.size(); i++) {
                String question = questions.get(i);
                QuestionData questionData = new QuestionData().text(question).sequenceNo(i + 1).key("Q" + (i + 1))
                        .description("Question " + (i + 1));

                List<ResponseData> responseDataList = new ArrayList<>();
                responseDataList.add(new ResponseData().text("Yes").value(1).sequenceNo(1));
                responseDataList.add(new ResponseData().text("No").value(0).sequenceNo(2));

                questionData.responseDatas(responseDataList);
                questionDataList.add(questionData);
            }

            surveyData.questionDatas(questionDataList);

            surveysApi.createSurvey(surveyData).execute();
            return surveyData.getId();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create survey: " + e.getMessage(), e);
        }
    }

    public SurveyData retrieveSurvey(Long surveyId) {
        try {
            return surveysApi.findSurvey(surveyId).execute().body();
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve survey: " + e.getMessage(), e);
        }
    }

    public String getSurveyName(SurveyData survey) {
        return survey.getName();
    }

    public String getSurveyDescription(SurveyData survey) {
        return survey.getDescription();
    }

    public LocalDate getSurveyValidFrom(SurveyData survey) {
        return survey.getValidFrom();
    }

    public LocalDate getSurveyValidTo(SurveyData survey) {
        return survey.getValidTo();
    }

    public int getSurveyQuestionsCount(SurveyData survey) {
        return survey.getQuestionDatas().size();
    }
}
