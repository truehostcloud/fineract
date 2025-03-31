package org.apache.fineract.portfolio.self.security.api;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.portfolio.self.security.service.SelfServicePasswordResetWritePlatformService;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Path("/v1/self/password")
@Component
@Tag(name = "Self Service Password Reset", description = "")
@RequiredArgsConstructor
public class SelfServicePasswordResetApiResource {

    private final FromJsonHelper fromApiJsonHelper;
    private final ToApiJsonSerializer<CommandProcessingResult> toApiJsonSerializer;
    private final SelfServicePasswordResetWritePlatformService selfServicePasswordResetWritePlatformService;
    private static final Set<String> SUPPORTED_PARAMETERS = new HashSet<>(Set.of("email"));

    @POST
    @Path("reset")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Request Password Reset", description = "If an account exists with the provided email address, a password reset token will be sent to that email address")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SelfServicePasswordResetApiResourceSwagger.PostSelfServicePasswordResetResponse.class))) })
    public String requestPasswordReset(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        if (StringUtils.isBlank(apiRequestBodyAsJson)) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, apiRequestBodyAsJson, SUPPORTED_PARAMETERS);

        final JsonElement element = this.fromApiJsonHelper.parse(apiRequestBodyAsJson);
        final JsonCommand command = JsonCommand.fromJsonElement(1L, element, this.fromApiJsonHelper);
        final CommandProcessingResult result = this.selfServicePasswordResetWritePlatformService.requestPasswordReset(command);
        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("verify")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Verify Password Reset Token", description = "Verifies the password reset token and allows setting a new password")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SelfServicePasswordResetApiResourceSwagger.PostSelfServicePasswordResetVerifyResponse.class))) })
    public String verifyPasswordResetToken(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        if (StringUtils.isBlank(apiRequestBodyAsJson)) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, apiRequestBodyAsJson, Set.of("token", "newPassword"));

        final JsonElement element = this.fromApiJsonHelper.parse(apiRequestBodyAsJson);
        final JsonCommand command = JsonCommand.fromJsonElement(1L, element, this.fromApiJsonHelper);
        final CommandProcessingResult result = this.selfServicePasswordResetWritePlatformService.verifyAndUpdatePassword(command);
        return this.toApiJsonSerializer.serialize(result);
    }
} 