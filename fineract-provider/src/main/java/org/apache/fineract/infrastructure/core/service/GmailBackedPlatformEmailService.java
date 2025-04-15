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
package org.apache.fineract.infrastructure.core.service;

import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.regex.Pattern;
import org.apache.fineract.infrastructure.configuration.data.SMTPCredentialsData;
import org.apache.fineract.infrastructure.configuration.service.ExternalServicesPropertiesReadPlatformService;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class GmailBackedPlatformEmailService implements PlatformEmailService {

    private final ExternalServicesPropertiesReadPlatformService externalServicesReadPlatformService;
    private static final Pattern HTML_PATTERN = Pattern
            .compile("(?i)<(\\w+)((\\s+[\\w-]+(=(\"[^\"]*\"|'[^']*'|[^'\">\\s]+))?)+\\s*|\\s*)/?>|(&[a-zA-Z]+;)|\\{\\{.*?\\}\\}");

    @Autowired
    public GmailBackedPlatformEmailService(final ExternalServicesPropertiesReadPlatformService externalServicesReadPlatformService) {
        this.externalServicesReadPlatformService = externalServicesReadPlatformService;
    }

    private boolean isHtmlContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        return HTML_PATTERN.matcher(content).find();
    }

    @Override
    public void sendToUserAccount(String organisationName, String contactName, String address, String username, String unencodedPassword) {

        final String subject = "Welcome " + contactName + " to " + organisationName;
        final String body = "You are receiving this email as your email account: " + address
                + " has being used to create a user account for an organisation named [" + organisationName + "] on Mifos.\n"
                + "You can login using the following credentials:\nusername: " + username + "\n" + "password: " + unencodedPassword + "\n"
                + "You must change this password upon first log in using Uppercase, Lowercase, number and character.\n"
                + "Thank you and welcome to the organisation.";

        final EmailDetail emailDetail = new EmailDetail(subject, body, address, contactName);
        sendDefinedEmail(emailDetail);
    }

    @Override
    public void sendDefinedEmail(EmailDetail emailDetails) {
        final SMTPCredentialsData smtpCredentialsData = this.externalServicesReadPlatformService.getSMTPCredentials();

        final String authuser = smtpCredentialsData.getUsername();
        final String authpwd = smtpCredentialsData.getPassword();

        final JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(smtpCredentialsData.getHost()); // smtp.gmail.com
        mailSender.setPort(Integer.parseInt(smtpCredentialsData.getPort())); // 587

        // Important: Enable less secure app access for the gmail account used in the following authentication

        mailSender.setUsername(authuser); // use valid gmail address
        mailSender.setPassword(authpwd); // use password of the above gmail account

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.debug", "true");

        // these are the added lines
        props.put("mail.smtp.starttls.enable", "true");
        // props.put("mail.smtp.ssl.enable", "true");

        props.put("mail.smtp.socketFactory.port", Integer.parseInt(smtpCredentialsData.getPort()));
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");// NOSONAR
        props.put("mail.smtp.socketFactory.fallback", "true");

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(smtpCredentialsData.getFromEmail());
            helper.setTo(emailDetails.getAddress());
            helper.setSubject(emailDetails.getSubject());

            boolean isHtml = isHtmlContent(emailDetails.getBody());
            if (isHtml) {
                String plainText = convertHtmlToPlainText(emailDetails.getBody());
                helper.setText(plainText, emailDetails.getBody());
            } else {
                helper.setText(emailDetails.getBody());
            }

            mailSender.send(message);
        } catch (Exception e) {
            throw new PlatformEmailSendException(e);
        }
    }

    private String convertHtmlToPlainText(String html) {
        if (html == null) {
            return "";
        }

        String text = html.replaceAll("<!DOCTYPE[^>]*>", "").replaceAll("<head>.*?</head>", "").replaceAll("<style>.*?</style>", "")
                .replaceAll("<!--.*?-->", "").replaceAll("<script>.*?</script>", "");

        text = text.replaceAll("<br\\s*/?>|</div>|</p>|</tr>", "\n").replaceAll("<li\\s*>", "* ").replaceAll("</li>", "\n")
                .replaceAll("<[^>]+>", "").replaceAll("&nbsp;", " ").replaceAll("&amp;", "&").replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">").replaceAll("&quot;", "\"").replaceAll("&apos;", "'");

        text = text.replaceAll("\\s+", " ").replaceAll(" *\\n *", "\n").replaceAll("\\n\\n+", "\n\n").trim();

        return text;
    }
}
