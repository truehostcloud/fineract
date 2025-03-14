package org.apache.fineract.infrastructure.documentmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.documentmanagement.command.DocumentCommand;
import org.apache.fineract.infrastructure.documentmanagement.data.DocumentData;
import org.apache.fineract.infrastructure.documentmanagement.data.FileData;
import org.apache.fineract.infrastructure.documentmanagement.service.DocumentReadPlatformService;
import org.apache.fineract.infrastructure.documentmanagement.service.DocumentWritePlatformService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.springframework.stereotype.Component;

@Component
@Path("/v1/self/{entityType}/{entityId}/documents")
@Tag(name = "Self Service Documents", description = "Documents API for self service users")
@RequiredArgsConstructor
public class SelfServiceDocumentManagementApiResource {

    private static final Set<String> RESPONSE_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList("id", "parentEntityType", "parentEntityId", "name", "fileName", "size", "type", "description"));

    private static final String SYSTEM_ENTITY_TYPE = "DOCUMENT";

    private final PlatformSecurityContext context;
    private final DocumentReadPlatformService documentReadPlatformService;
    private final DocumentWritePlatformService documentWritePlatformService;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final ToApiJsonSerializer<DocumentData> toApiJsonSerializer;
    private final FileUploadValidator fileUploadValidator;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List documents", description = "Example Requests:\n" + "\n" + "self/clients/1/documents\n" + "\n"
            + "self/client_identifiers/1/documents\n" + "\n" + "self/loans/1/documents?fields=name,description")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = DocumentManagementApiResourceSwagger.GetEntityTypeEntityIdDocumentsResponse.class)))) })
    public String retrieveAllDocuments(@Context final UriInfo uriInfo,
            @PathParam("entityType") @Parameter(description = "entityType") final String entityType,
            @PathParam("entityId") @Parameter(description = "entityId") final Long entityId) {

        validateUserHasAccessToEntity(entityType, entityId);

        final Collection<DocumentData> documentDatas = this.documentReadPlatformService.retrieveAllDocuments(entityType, entityId);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, documentDatas, RESPONSE_DATA_PARAMETERS);
    }

    @POST
    @Consumes({ MediaType.MULTIPART_FORM_DATA })
    @Produces({ MediaType.APPLICATION_JSON })
    @RequestBody(description = "Create document", content = {
            @Content(mediaType = MediaType.MULTIPART_FORM_DATA, schema = @Schema(implementation = DocumentManagementApiResourceSwagger.DocumentUploadRequest.class)) })
    @Operation(summary = "Create a Document")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DocumentManagementApiResourceSwagger.PostEntityTypeEntityIdDocumentsResponse.class))) })
    public String createDocument(@PathParam("entityType") @Parameter(description = "entityType") final String entityType,
            @PathParam("entityId") @Parameter(description = "entityId") final Long entityId,
            @HeaderParam("Content-Length") @Parameter(description = "Content-Length") final Long fileSize,
            @FormDataParam("file") final InputStream inputStream, @FormDataParam("file") final FormDataContentDisposition fileDetails,
            @FormDataParam("file") final FormDataBodyPart bodyPart, @FormDataParam("name") final String name,
            @FormDataParam("description") final String description) {

        validateUserHasAccessToEntity(entityType, entityId);
        
        fileUploadValidator.validate(fileSize, inputStream, fileDetails, bodyPart);
        final DocumentCommand documentCommand = new DocumentCommand(null, null, entityType, entityId, name, fileDetails.getFileName(),
                fileSize, bodyPart.getMediaType().toString(), description, null);
        final Long documentId = this.documentWritePlatformService.createDocument(documentCommand, inputStream);
        return this.toApiJsonSerializer.serialize(CommandProcessingResult.resourceResult(documentId));
    }

    @PUT
    @Path("{documentId}")
    @Consumes({ MediaType.MULTIPART_FORM_DATA })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update a Document")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DocumentManagementApiResourceSwagger.PutEntityTypeEntityIdDocumentsResponse.class))) })
    public String updateDocument(@PathParam("entityType") @Parameter(description = "entityType") final String entityType,
            @PathParam("entityId") @Parameter(description = "entityId") final Long entityId,
            @PathParam("documentId") @Parameter(description = "documentId") final Long documentId,
            @HeaderParam("Content-Length") @Parameter(description = "Content-Length") final Long fileSize,
            @FormDataParam("file") final InputStream inputStream, @FormDataParam("file") final FormDataContentDisposition fileDetails,
            @FormDataParam("file") final FormDataBodyPart bodyPart, @FormDataParam("name") final String name,
            @FormDataParam("description") final String description) {

        validateUserHasAccessToEntity(entityType, entityId);

        Set<String> modifiedParams = new HashSet<>(Arrays.asList("name", "description"));
        DocumentCommand documentCommand;

        if (inputStream != null && fileDetails.getFileName() != null) {
            fileUploadValidator.validate(fileSize, inputStream, fileDetails, bodyPart);
            modifiedParams.addAll(Arrays.asList("fileName", "size", "type", "location"));
            documentCommand = new DocumentCommand(modifiedParams, documentId, entityType, entityId, name, fileDetails.getFileName(),
                    fileSize, bodyPart.getMediaType().toString(), description, null);
        } else {
            documentCommand = new DocumentCommand(modifiedParams, documentId, entityType, entityId, name, null, null, null, description,
                    null);
        }

        final CommandProcessingResult identifier = this.documentWritePlatformService.updateDocument(documentCommand, inputStream);
        return this.toApiJsonSerializer.serialize(identifier);
    }

    @GET
    @Path("{documentId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a Document")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DocumentManagementApiResourceSwagger.GetEntityTypeEntityIdDocumentsResponse.class))) })
    public String getDocument(@PathParam("entityType") @Parameter(description = "entityType") final String entityType,
            @PathParam("entityId") @Parameter(description = "entityId") final Long entityId,
            @PathParam("documentId") @Parameter(description = "documentId") final Long documentId,
            @Context final UriInfo uriInfo) {

        validateUserHasAccessToEntity(entityType, entityId);

        final DocumentData documentData = this.documentReadPlatformService.retrieveDocument(entityType, entityId, documentId);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, documentData, RESPONSE_DATA_PARAMETERS);
    }

    @GET
    @Path("{documentId}/attachment")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_OCTET_STREAM })
    @Operation(summary = "Retrieve Binary File associated with Document")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "OK") })
    public Response downloadFile(@PathParam("entityType") @Parameter(description = "entityType") final String entityType,
            @PathParam("entityId") @Parameter(description = "entityId") final Long entityId,
            @PathParam("documentId") @Parameter(description = "documentId") final Long documentId) {

        validateUserHasAccessToEntity(entityType, entityId);
        
        final FileData fileData = this.documentReadPlatformService.retrieveFileData(entityType, entityId, documentId);
        return ContentResources.fileDataToResponse(fileData, "attachment");
    }

    @DELETE
    @Path("{documentId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Remove a Document")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DocumentManagementApiResourceSwagger.DeleteEntityTypeEntityIdDocumentsResponse.class))) })
    public String deleteDocument(@PathParam("entityType") @Parameter(description = "entityType") final String entityType,
            @PathParam("entityId") @Parameter(description = "entityId") final Long entityId,
            @PathParam("documentId") @Parameter(description = "documentId") final Long documentId) {

        validateUserHasAccessToEntity(entityType, entityId);

        final DocumentCommand documentCommand = new DocumentCommand(null, documentId, entityType, entityId, null, null, null, null, null,
                null);
        final CommandProcessingResult documentIdentifier = this.documentWritePlatformService.deleteDocument(documentCommand);

        return this.toApiJsonSerializer.serialize(documentIdentifier);
    }

    private void validateUserHasAccessToEntity(String entityType, Long entityId) {
        AppUser user = context.authenticatedUser();
        
        if (!user.isSelfServiceUser()) {
            throw new NoAuthorizationException("User is not a self-service user");
        }

        user.validateHasReadPermission(SYSTEM_ENTITY_TYPE);

        switch (entityType.toLowerCase()) {
            case "clients":
                boolean hasAccess = user.getAppUserClientMappings().stream()
                    .anyMatch(mapping -> mapping.getClient().getId().equals(entityId));
                if (!hasAccess) {
                    throw new NoAuthorizationException("User does not have access to this client");
                }
                break;
            // Add cases for other entity types as needed (loans, savings etc)
            default:
                throw new NoAuthorizationException("Entity type not supported for self-service: " + entityType);
        }
    }
}