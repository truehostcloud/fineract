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
package org.apache.fineract.infrastructure.hooks.processor;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.hooks.domain.Hook;
import org.apache.fineract.infrastructure.hooks.domain.HookConfiguration;
import org.apache.fineract.portfolio.client.data.ClientData;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.service.LoanAssembler;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.springframework.stereotype.Service;
import retrofit2.Callback;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.apache.fineract.infrastructure.hooks.api.HookApiConstants.contentTypeName;
import static org.apache.fineract.infrastructure.hooks.api.HookApiConstants.payloadURLName;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebHookProcessor implements HookProcessor {

    private final ProcessorHelper processorHelper;
    private final LoanAssembler loanAssembler;
    private final ClientReadPlatformService clientReadPlatformService;

    @Override
    public void process(final Hook hook, final String payload, final String entityName, final String actionName,
                        final FineractContext context) {

        final Set<HookConfiguration> config = hook.getConfig();

        String url = "";
        String contentType = "";

        for (final HookConfiguration conf : config) {
            final String fieldName = conf.getFieldName();
            if (fieldName.equals(payloadURLName)) {
                url = conf.getFieldValue();
            }
            if (fieldName.equals(contentTypeName)) {
                contentType = conf.getFieldValue();
            }
        }

        String enhancedPayload = payload;
        if ("LOAN".equals(entityName) && "DISBURSE".equals(actionName)) {
            enhancedPayload = enhanceLoanDisbursementPayload(payload);
        }

        sendRequest(url, contentType, enhancedPayload, entityName, actionName, context);
    }

    private String enhanceLoanDisbursementPayload(String originalPayload) {
        try {
            JsonObject originalJson = JsonParser.parseString(originalPayload).getAsJsonObject();

            JsonObject response = originalJson.getAsJsonObject("response");
            if (response != null && response.has("loanId")) {
                Long loanId = response.get("loanId").getAsLong();

                Map<String, Object> enhancedData = getEnhancedLoanData(loanId);

                originalJson.add("data", new Gson().toJsonTree(enhancedData));

                return originalJson.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to enhance loan disbursement payload", e);
        }

        return originalPayload;
    }

    private Map<String, Object> getEnhancedLoanData(Long loanId) {
        Map<String, Object> enhancedData = new HashMap<>();

        try {
            Loan loan = loanAssembler.assembleFrom(loanId);

            ClientData clientData = clientReadPlatformService.retrieveOne(loan.getClientId());

            PaymentDetail paymentDetail = getPaymentDetailFromLoan(loan);

            enhancedData.put("loanId", loan.getId());
            enhancedData.put("loanAccountNumber", loan.getAccountNumber());
            enhancedData.put("disbursalAmount", loan.getNetDisbursalAmount());
            enhancedData.put("currency", loan.getCurrencyCode());
            enhancedData.put("disbursalDate", loan.getActualDisbursementDate() != null ?
                    loan.getActualDisbursementDate().toString() : null);
            enhancedData.put("fund", loan.getFund());

            Map<String, Object> clientInfo = buildClientInfo(clientData);
            enhancedData.put("client", clientInfo);

            if (paymentDetail != null) {
                Map<String, Object> paymentInfo = buildPaymentInfo(paymentDetail);
                enhancedData.put("paymentDetails", paymentInfo);
            }

            enhancedData.put("officeId", loan.getOfficeId());
            enhancedData.put("officeName", loan.getOffice().getName());

            enhancedData.put("loanProductId", loan.getLoanProduct().getId());
            enhancedData.put("loanProductName", loan.getLoanProduct().getName());

        } catch (Exception e) {
            log.warn("Error getting enhanced loan data for loanId: {}", loanId, e);
        }

        return enhancedData;
    }

    private Map<String, Object> buildClientInfo(ClientData clientData) {
        Map<String, Object> clientInfo = new HashMap<>();
        clientInfo.put("clientId", clientData.getId());
        clientInfo.put("name", clientData.getDisplayName());
        clientInfo.put("mobileNumber", clientData.getMobileNo());
        clientInfo.put("emailAddress", clientData.getEmailAddress());
        clientInfo.put("externalId", clientData.getExternalId());
        return clientInfo;
    }

    private Map<String, Object> buildPaymentInfo(PaymentDetail paymentDetail) {
        Map<String, Object> paymentInfo = new HashMap<>();
        if (paymentDetail.getPaymentType() != null) {
            paymentInfo.put("paymentType", paymentDetail.getPaymentType().getName());
            paymentInfo.put("paymentTypeId", paymentDetail.getPaymentType().getId());
        }
        paymentInfo.put("accountNumber", paymentDetail.getAccountNumber());
        paymentInfo.put("checkNumber", paymentDetail.getCheckNumber());
        paymentInfo.put("receiptNumber", paymentDetail.getReceiptNumber());
        paymentInfo.put("routingCode", paymentDetail.getRoutingCode());
        paymentInfo.put("bankNumber", paymentDetail.getBankNumber());
        return paymentInfo;
    }

    private PaymentDetail getPaymentDetailFromLoan(Loan loan) {
        return loan.getLoanTransactions().stream()
                .filter(transaction -> transaction.getTypeOf().isDisbursement())
                .findFirst()
                .map(LoanTransaction::getPaymentDetail)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private void sendRequest(final String url, final String contentType, final String payload, final String entityName,
                             final String actionName, final FineractContext context) {

        final String fineractEndpointUrl = System.getProperty("baseUrl");
        final WebHookService service = processorHelper.createWebHookService(url);

        @SuppressWarnings("rawtypes")
        final Callback callback = processorHelper.createCallback(url);

        if (contentType.equalsIgnoreCase("json") || contentType.contains("json")) {
            final JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            service.sendJsonRequest(entityName, actionName, context.getTenantContext().getTenantIdentifier(), fineractEndpointUrl, json)
                    .enqueue(callback);
        } else {
            Map<String, String> map = new HashMap<>();
            map = new Gson().fromJson(payload, map.getClass());
            service.sendFormRequest(entityName, actionName, context.getTenantContext().getTenantIdentifier(), fineractEndpointUrl, map)
                    .enqueue(callback);
        }
    }
}
