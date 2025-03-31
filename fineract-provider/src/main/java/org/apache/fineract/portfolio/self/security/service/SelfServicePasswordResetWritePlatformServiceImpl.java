package org.apache.fineract.portfolio.self.security.service;

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

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
        return new CommandProcessingResultBuilder()
                .withCommandId(command.commandId())
                .build();
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
            errors.add(ApiParameterError.parameterError("error.msg.password.reset.token.expired", "Password reset token has expired", "token"));
            throw new PlatformApiDataValidationException(errors);
        }

        validatePassword(newPassword);

        PlatformUser platformUser = new BasicPasswordEncodablePlatformUser()
                .setId(user.getId())
                .setUsername(user.getUsername())
                .setPassword(newPassword);
        String encodedPassword = this.platformPasswordEncoder.encode(platformUser);
        user.updatePassword(encodedPassword);
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        this.appUserRepository.save(user);

        return new CommandProcessingResultBuilder()
                .withCommandId(command.commandId())
                .withEntityId(user.getId())
                .build();
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
        final String subject = "Password Reset Request";
        final String body = String.format("Dear %s,\n\nYou have requested to reset your password. Please use the following token to reset your password:\n\n%s\n\nThis token will expire in 24 hours.\n\nIf you did not request this password reset, please ignore this email.\n\nBest regards,\nJisort Team",
                user.getFirstname(), resetToken);

        this.gmailBackedPlatformEmailService.sendDefinedEmail(new org.apache.fineract.infrastructure.core.domain.EmailDetail(subject, body, user.getEmail(), user.getFirstname()));
    }
} 