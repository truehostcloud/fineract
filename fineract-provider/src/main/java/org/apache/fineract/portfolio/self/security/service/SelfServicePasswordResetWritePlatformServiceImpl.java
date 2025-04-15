package org.apache.fineract.portfolio.self.security.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.GmailBackedPlatformEmailService;
import org.apache.fineract.infrastructure.security.domain.BasicPasswordEncodablePlatformUser;
import org.apache.fineract.infrastructure.security.domain.PlatformUser;
import org.apache.fineract.infrastructure.security.service.PlatformPasswordEncoder;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.domain.PasswordValidationPolicy;
import org.apache.fineract.useradministration.domain.PasswordValidationPolicyRepository;
import org.apache.fineract.useradministration.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SelfServicePasswordResetWritePlatformServiceImpl implements SelfServicePasswordResetWritePlatformService {

    private final AppUserRepository appUserRepository;
    private final GmailBackedPlatformEmailService gmailBackedPlatformEmailService;
    private final PlatformPasswordEncoder platformPasswordEncoder;
    private final PasswordValidationPolicyRepository passwordValidationPolicyRepository;
    private static final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public CommandProcessingResult requestPasswordReset(JsonCommand command) {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("password.reset");

        final String email = command.stringValueOfParameterNamed("email");
        baseDataValidator.reset().parameter("email").value(email).notBlank().notExceedingLengthOf(100);

        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        AppUser user = this.appUserRepository.findByEmail(email);

        if (user != null && user.isSelfServiceUser()) {
            String resetToken = generateResetToken();
            user.setPasswordResetToken(resetToken);
            user.setPasswordResetTokenExpiry(LocalDateTime.now().plusHours(24));
            this.appUserRepository.save(user);

            sendPasswordResetEmail(user, resetToken);
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult verifyAndUpdatePassword(JsonCommand command) {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("password.reset.verify");

        final String token = command.stringValueOfParameterNamed("token");
        baseDataValidator.reset().parameter("token").value(token).notBlank();

        final String newPassword = command.stringValueOfParameterNamed("newPassword");
        baseDataValidator.reset().parameter("newPassword").value(newPassword).notBlank();

        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        AppUser user = this.appUserRepository.findByPasswordResetToken(token);
        if (user == null) {
            throw new UserNotFoundException("Invalid or expired reset token");
        }

        if (user.getPasswordResetTokenExpiry().isBefore(LocalDateTime.now())) {
            final List<ApiParameterError> errors = new ArrayList<>();
            errors.add(ApiParameterError.parameterError("error.msg.password.reset.token.expired", "Password reset token has expired",
                    "token"));
            throw new PlatformApiDataValidationException(errors);
        }

        validatePassword(newPassword);

        PlatformUser platformUser = new BasicPasswordEncodablePlatformUser().setId(user.getId()).setUsername(user.getUsername())
                .setPassword(newPassword);
        String encodedPassword = this.platformPasswordEncoder.encode(platformUser);
        user.updatePassword(encodedPassword);
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        this.appUserRepository.save(user);

        sendPasswordResetConfirmationEmail(user, command);

        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(user.getId()).build();
    }

    private void validatePassword(String password) {
        final PasswordValidationPolicy validationPolicy = this.passwordValidationPolicyRepository.findActivePasswordValidationPolicy();
        final String regex = validationPolicy.getRegex();
        final String description = validationPolicy.getDescription();

        if (!password.matches(regex)) {
            final List<ApiParameterError> errors = new ArrayList<>();
            errors.add(ApiParameterError.parameterError("error.msg.password.does.not.match.policy", description, "newPassword"));
            throw new PlatformApiDataValidationException(errors);
        }
    }

    private String generateResetToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private void sendPasswordResetEmail(AppUser user, String resetToken) {
        final String subject = "Mifos: Your Password Reset Request";
        final String body = String.format("<!DOCTYPE html>" + "<html>" + "<head>" + "<meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" + "<style>"
                + "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; line-height: 1.7; color: #333; background-color: #f5f5f5; margin: 0; padding: 0; }"
                + ".container { max-width: 600px; margin: 20px auto; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.1); background: white; }"
                + ".header { background: linear-gradient(135deg, #0066cc, #004c99); color: white; padding: 25px; text-align: center; }"
                + ".header h2 { margin: 0; font-weight: 600; letter-spacing: 0.5px; }"
                + ".content { padding: 30px; background-color: white; }"
                + ".greeting { font-size: 18px; font-weight: 500; margin-bottom: 20px; }"
                + ".token { background-color: #f0f7ff; border: 1px solid #cce5ff; border-radius: 6px; padding: 15px; margin: 25px 0; font-family: 'Courier New', monospace; text-align: center; font-size: 20px; font-weight: bold; color: #0066cc; letter-spacing: 1px; }"
                + ".important { background-color: #fff8e6; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0; }"
                + ".important strong { color: #e65100; }"
                + ".footer { background-color: #f9f9f9; border-top: 1px solid #eee; padding: 20px; text-align: center; font-size: 13px; color: #777; }"
                + ".logo { margin-bottom: 15px; }" + ".logo img { height: 40px; }" + ".social { margin-top: 15px; }"
                + ".social a { display: inline-block; margin: 0 8px; color: #0066cc; text-decoration: none; }" + "</style>" + "</head>"
                + "<body>" + "<div class='container'>" + "<div class='header'>" + "<h2>Password Reset Request</h2>" + "</div>"
                + "<div class='content'>" + "<p class='greeting'>Dear %s,</p>"
                + "<p>We received a request to reset your password for your Mifos account. To proceed with the password reset, please use the following token:</p>"
                + "<div class='token'>%s</div>" + "<p>This token will expire in <strong>24 hours</strong> for security purposes.</p>"
                + "<div class='important'>"
                + "<strong>Important:</strong> If you did not request this password reset, please contact your system administrator immediately."
                + "</div>" + "</div>" + "<div class='footer'>"
                + "<p>This is an automated message from Mifos. Please do not reply to this email.</p>"
                + "<p>&copy; %d Mifos. All rights reserved.</p>" + "</div>" + "</div>" + "</body>" + "</html>", user.getFirstname(),
                resetToken, LocalDateTime.now().getYear());

        this.gmailBackedPlatformEmailService.sendDefinedEmail(
                new org.apache.fineract.infrastructure.core.domain.EmailDetail(subject, body, user.getEmail(), user.getFirstname()));
    }

    private void sendPasswordResetConfirmationEmail(AppUser user, JsonCommand command) {
        final String subject = "Mifos: Password Reset Successful";
        final String body = String.format("<!DOCTYPE html>" + "<html>" + "<head>" + "<meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" + "<style>"
                + "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; line-height: 1.7; color: #333; background-color: #f5f5f5; margin: 0; padding: 0; }"
                + ".container { max-width: 600px; margin: 20px auto; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.1); background: white; }"
                + ".header { background: linear-gradient(135deg, #0066cc, #004c99); color: white; padding: 25px; text-align: center; }"
                + ".header h2 { margin: 0; font-weight: 600; letter-spacing: 0.5px; }"
                + ".content { padding: 30px; background-color: white; }"
                + ".greeting { font-size: 18px; font-weight: 500; margin-bottom: 20px; }"
                + ".credentials { background-color: #f0f7ff; border-left: 4px solid #0066cc; border-radius: 6px; padding: 20px; margin: 25px 0; }"
                + ".credentials p { margin: 10px 0; }" + ".credentials strong { display: inline-block; width: 100px; }"
                + ".warning-box { background-color: #fff5f5; border-left: 4px solid #ff3b30; border-radius: 6px; padding: 20px; margin: 25px 0; }"
                + ".warning-title { color: #cc0000; font-weight: bold; margin-bottom: 10px; }"
                + ".warning-list { padding-left: 20px; margin: 15px 0; }" + ".warning-list li { margin-bottom: 8px; }"
                + ".footer { background-color: #f9f9f9; border-top: 1px solid #eee; padding: 20px; text-align: center; font-size: 13px; color: #777; }"
                + ".cta-button { display: inline-block; background-color: #0066cc; color: white; text-decoration: none; padding: 12px 25px; border-radius: 5px; font-weight: 500; margin: 20px 0; }"
                + "</style>" + "</head>" + "<body>" + "<div class='container'>" + "<div class='header'>"
                + "<h2>Password Reset Successful</h2>" + "</div>" + "<div class='content'>" + "<p class='greeting'>Dear %s,</p>"
                + "<p>Your password has been successfully reset. Here are your login credentials:</p>" + "<div class='credentials'>"
                + "<p><strong>Username:</strong> %s</p>" + "<p><strong>Password:</strong> %s</p>" + "</div>" + "<div class='warning-box'>"
                + "<div class='warning-title'>Important Security Notice:</div>" + "<ul class='warning-list'>"
                + "<li>For security reasons, please do not share these credentials with anyone</li>" + "</ul>" + "</div>"
                + "<p>If you did not perform this password reset, please contact your system administrator immediately.</p>" + "</div>"
                + "<div class='footer'>" + "<p>This is an automated message from Mifos. Please do not reply to this email.</p>"
                + "<p>&copy; %d Mifos. All rights reserved.</p>" + "</div>" + "</div>" + "</body>" + "</html>", user.getFirstname(),
                user.getUsername(), command.stringValueOfParameterNamed("newPassword"), LocalDateTime.now().getYear());

        this.gmailBackedPlatformEmailService.sendDefinedEmail(
                new org.apache.fineract.infrastructure.core.domain.EmailDetail(subject, body, user.getEmail(), user.getFirstname()));
    }
}
