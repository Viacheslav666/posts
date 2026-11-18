package ru.posts.api.ord;

import java.util.List;
import java.util.UUID;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.ord.OrdDtos;

public interface OrdService {

    CursorPage.OrdConnectionPage listConnections(UUID userId, UUID workspaceId);

    OrdDtos.OrdConnection upsertConnection(
            UUID userId,
            UUID workspaceId,
            OrdDtos.OrdProviderCode provider,
            OrdDtos.UpsertOrdConnectionRequest request,
            ClientRequestContext ctx);

    OrdDtos.OrdConnectionTestResponse testConnection(
            UUID userId, UUID workspaceId, OrdDtos.OrdProviderCode provider, ClientRequestContext ctx);

    CursorPage.OrdContractorPage listContractors(UUID userId, UUID workspaceId);

    OrdDtos.OrdContractor createContractor(
            UUID userId, UUID workspaceId, OrdDtos.CreateOrdContractorRequest request, ClientRequestContext ctx);

    OrdDtos.OrdContractor getContractor(UUID userId, UUID workspaceId, UUID contractorId);

    OrdDtos.OrdContractor updateContractor(
            UUID userId,
            UUID workspaceId,
            UUID contractorId,
            OrdDtos.UpdateOrdContractorRequest request,
            ClientRequestContext ctx);

    CursorPage.OrdContractPage listContracts(UUID userId, UUID workspaceId);

    OrdDtos.OrdContract createContract(
            UUID userId, UUID workspaceId, OrdDtos.CreateOrdContractRequest request, ClientRequestContext ctx);

    List<OrdDtos.DictionaryKktuItem> searchKktu(String q);

    List<OrdDtos.DictionaryOksmItem> searchOksm(String q);

    OrdDtos.OrdRegisterCreativeResponse registerCreative(UUID userId, UUID postId, ClientRequestContext ctx);

    OrdDtos.OrdCreativeStatus creativeStatus(UUID userId, UUID postId);

    OrdDtos.OrdReportRunResponse runReport(UUID userId, OrdDtos.OrdReportRunRequest request, ClientRequestContext ctx);
}
