package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.auth.ClientRequestContexts;
import ru.posts.api.ord.OrdService;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.ord.OrdDtos;
import ru.posts.security.auth.SecurityAuthSupport;

@RestController
@Validated
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "ORD", description = "Ad marking / ORD connections, contractors, creatives")
@OpenApiResponses
public class OrdController {

    private final OrdService ordService;

    public OrdController(OrdService ordService) {
        this.ordService = ordService;
    }

    @GetMapping("/workspaces/{workspaceId}/ord/connections")
    @Operation(operationId = "ordConnectionsList", summary = "List ORD connections for a workspace")
    @ApiResponse(responseCode = "200", description = "Connections page")
    public CursorPage.OrdConnectionPage listConnections(@PathVariable UUID workspaceId) {
        return ordService.listConnections(SecurityAuthSupport.requirePrincipal().userId(), workspaceId);
    }

    @PutMapping("/workspaces/{workspaceId}/ord/connections/{provider}")
    @Operation(operationId = "ordConnectionsUpsert", summary = "Create or update an ORD connection")
    @ApiResponse(
            responseCode = "200",
            description = "Upserted connection",
            content = @Content(schema = @Schema(implementation = OrdDtos.OrdConnection.class)))
    public OrdDtos.OrdConnection upsertConnection(
            @PathVariable UUID workspaceId,
            @PathVariable OrdDtos.OrdProviderCode provider,
            @Valid @RequestBody OrdDtos.UpsertOrdConnectionRequest request,
            HttpServletRequest httpRequest) {
        return ordService.upsertConnection(
                SecurityAuthSupport.requirePrincipal().userId(),
                workspaceId,
                provider,
                request,
                ClientRequestContexts.from(httpRequest));
    }

    @PostMapping("/workspaces/{workspaceId}/ord/connections/{provider}/test")
    @Operation(operationId = "ordConnectionsTest", summary = "Test ORD connection credentials")
    @ApiResponse(
            responseCode = "200",
            description = "Test result",
            content = @Content(schema = @Schema(implementation = OrdDtos.OrdConnectionTestResponse.class)))
    public OrdDtos.OrdConnectionTestResponse testConnection(
            @PathVariable UUID workspaceId,
            @PathVariable OrdDtos.OrdProviderCode provider,
            HttpServletRequest httpRequest) {
        return ordService.testConnection(
                SecurityAuthSupport.requirePrincipal().userId(),
                workspaceId,
                provider,
                ClientRequestContexts.from(httpRequest));
    }

    @GetMapping("/workspaces/{workspaceId}/ord/contractors")
    @Operation(operationId = "ordContractorsList", summary = "List ORD contractors")
    @ApiResponse(responseCode = "200", description = "Contractors page")
    public CursorPage.OrdContractorPage listContractors(@PathVariable UUID workspaceId) {
        return ordService.listContractors(SecurityAuthSupport.requirePrincipal().userId(), workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/ord/contractors")
    @Operation(operationId = "ordContractorsCreate", summary = "Create ORD contractor")
    @ApiResponse(
            responseCode = "200",
            description = "Created contractor",
            content = @Content(schema = @Schema(implementation = OrdDtos.OrdContractor.class)))
    public OrdDtos.OrdContractor createContractor(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody OrdDtos.CreateOrdContractorRequest request,
            HttpServletRequest httpRequest) {
        return ordService.createContractor(
                SecurityAuthSupport.requirePrincipal().userId(),
                workspaceId,
                request,
                ClientRequestContexts.from(httpRequest));
    }

    @GetMapping("/workspaces/{workspaceId}/ord/contractors/{contractorId}")
    @Operation(operationId = "ordContractorsGet", summary = "Get ORD contractor")
    @ApiResponse(
            responseCode = "200",
            description = "Contractor",
            content = @Content(schema = @Schema(implementation = OrdDtos.OrdContractor.class)))
    public OrdDtos.OrdContractor getContractor(@PathVariable UUID workspaceId, @PathVariable UUID contractorId) {
        return ordService.getContractor(SecurityAuthSupport.requirePrincipal().userId(), workspaceId, contractorId);
    }

    @PutMapping("/workspaces/{workspaceId}/ord/contractors/{contractorId}")
    @Operation(operationId = "ordContractorsUpdate", summary = "Update ORD contractor")
    @ApiResponse(
            responseCode = "200",
            description = "Updated contractor",
            content = @Content(schema = @Schema(implementation = OrdDtos.OrdContractor.class)))
    public OrdDtos.OrdContractor updateContractor(
            @PathVariable UUID workspaceId,
            @PathVariable UUID contractorId,
            @Valid @RequestBody OrdDtos.UpdateOrdContractorRequest request,
            HttpServletRequest httpRequest) {
        return ordService.updateContractor(
                SecurityAuthSupport.requirePrincipal().userId(),
                workspaceId,
                contractorId,
                request,
                ClientRequestContexts.from(httpRequest));
    }

    @GetMapping("/workspaces/{workspaceId}/ord/contracts")
    @Operation(operationId = "ordContractsList", summary = "List ORD contracts")
    @ApiResponse(responseCode = "200", description = "Contracts page")
    public CursorPage.OrdContractPage listContracts(@PathVariable UUID workspaceId) {
        return ordService.listContracts(SecurityAuthSupport.requirePrincipal().userId(), workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/ord/contracts")
    @Operation(operationId = "ordContractsCreate", summary = "Create ORD contract")
    @ApiResponse(
            responseCode = "200",
            description = "Created contract",
            content = @Content(schema = @Schema(implementation = OrdDtos.OrdContract.class)))
    public OrdDtos.OrdContract createContract(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody OrdDtos.CreateOrdContractRequest request,
            HttpServletRequest httpRequest) {
        return ordService.createContract(
                SecurityAuthSupport.requirePrincipal().userId(),
                workspaceId,
                request,
                ClientRequestContexts.from(httpRequest));
    }

    @GetMapping("/ord/dictionaries/kktu")
    @Operation(operationId = "ordDictionariesKktu", summary = "Search KKTU dictionary")
    @ApiResponse(
            responseCode = "200",
            description = "KKTU items",
            content =
                    @Content(array = @ArraySchema(schema = @Schema(implementation = OrdDtos.DictionaryKktuItem.class))))
    public List<OrdDtos.DictionaryKktuItem> dictionariesKktu(@RequestParam(required = false) String q) {
        return ordService.searchKktu(q);
    }

    @GetMapping("/ord/dictionaries/oksm")
    @Operation(operationId = "ordDictionariesOksm", summary = "Search OKSM dictionary")
    @ApiResponse(
            responseCode = "200",
            description = "OKSM items",
            content =
                    @Content(array = @ArraySchema(schema = @Schema(implementation = OrdDtos.DictionaryOksmItem.class))))
    public List<OrdDtos.DictionaryOksmItem> dictionariesOksm(@RequestParam(required = false) String q) {
        return ordService.searchOksm(q);
    }

    @PostMapping("/ord/vat/calculate")
    @Operation(operationId = "ordVatCalculate", summary = "Split amount with VAT into net + VAT (BE-25)")
    @ApiResponse(
            responseCode = "200",
            description = "VAT breakdown",
            content = @Content(schema = @Schema(implementation = OrdDtos.VatBreakdown.class)))
    public OrdDtos.VatBreakdown calculateVat(@Valid @RequestBody OrdDtos.OrdVatCalculateRequest request) {
        return ordService.calculateVat(request);
    }

    @PostMapping("/posts/{postId}/ord/register")
    @Operation(operationId = "postsOrdRegister", summary = "Register post creative in ORD (idempotent)")
    @ApiResponse(
            responseCode = "200",
            description = "Registration result",
            content = @Content(schema = @Schema(implementation = OrdDtos.OrdRegisterCreativeResponse.class)))
    public OrdDtos.OrdRegisterCreativeResponse register(@PathVariable UUID postId, HttpServletRequest httpRequest) {
        return ordService.registerCreative(
                SecurityAuthSupport.requirePrincipal().userId(), postId, ClientRequestContexts.from(httpRequest));
    }

    @GetMapping("/posts/{postId}/ord/status")
    @Operation(operationId = "postsOrdStatus", summary = "Get ORD creative status for a post")
    @ApiResponse(
            responseCode = "200",
            description = "Creative status",
            content = @Content(schema = @Schema(implementation = OrdDtos.OrdCreativeStatus.class)))
    public OrdDtos.OrdCreativeStatus status(@PathVariable UUID postId) {
        return ordService.creativeStatus(SecurityAuthSupport.requirePrincipal().userId(), postId);
    }

    @PostMapping("/ord/reports/run")
    @Operation(operationId = "ordReportsRun", summary = "Run ORD invoices/statistics for a period")
    @ApiResponse(
            responseCode = "200",
            description = "Report run",
            content = @Content(schema = @Schema(implementation = OrdDtos.OrdReportRunResponse.class)))
    public OrdDtos.OrdReportRunResponse runReport(
            @Valid @RequestBody OrdDtos.OrdReportRunRequest request, HttpServletRequest httpRequest) {
        return ordService.runReport(
                SecurityAuthSupport.requirePrincipal().userId(), request, ClientRequestContexts.from(httpRequest));
    }
}
