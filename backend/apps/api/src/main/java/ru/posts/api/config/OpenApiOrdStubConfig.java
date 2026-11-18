package ru.posts.api.config;

import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.error.NotImplementedException;
import ru.posts.api.ord.OrdService;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.ord.OrdDtos;

/** Stub so openapi profile can boot OrdController. */
@Configuration
@Profile("openapi")
public class OpenApiOrdStubConfig {

    @Bean
    OrdService ordService() {
        return new OrdService() {
            @Override
            public CursorPage.OrdConnectionPage listConnections(UUID userId, UUID workspaceId) {
                return new CursorPage.OrdConnectionPage(List.of(), null, false);
            }

            @Override
            public OrdDtos.OrdConnection upsertConnection(
                    UUID userId,
                    UUID workspaceId,
                    OrdDtos.OrdProviderCode provider,
                    OrdDtos.UpsertOrdConnectionRequest request,
                    ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public OrdDtos.OrdConnectionTestResponse testConnection(
                    UUID userId, UUID workspaceId, OrdDtos.OrdProviderCode provider, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public CursorPage.OrdContractorPage listContractors(UUID userId, UUID workspaceId) {
                return new CursorPage.OrdContractorPage(List.of(), null, false);
            }

            @Override
            public OrdDtos.OrdContractor createContractor(
                    UUID userId,
                    UUID workspaceId,
                    OrdDtos.CreateOrdContractorRequest request,
                    ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public OrdDtos.OrdContractor getContractor(UUID userId, UUID workspaceId, UUID contractorId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public OrdDtos.OrdContractor updateContractor(
                    UUID userId,
                    UUID workspaceId,
                    UUID contractorId,
                    OrdDtos.UpdateOrdContractorRequest request,
                    ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public CursorPage.OrdContractPage listContracts(UUID userId, UUID workspaceId) {
                return new CursorPage.OrdContractPage(List.of(), null, false);
            }

            @Override
            public OrdDtos.OrdContract createContract(
                    UUID userId, UUID workspaceId, OrdDtos.CreateOrdContractRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public List<OrdDtos.DictionaryKktuItem> searchKktu(String q) {
                return List.of();
            }

            @Override
            public List<OrdDtos.DictionaryOksmItem> searchOksm(String q) {
                return List.of();
            }

            @Override
            public OrdDtos.OrdRegisterCreativeResponse registerCreative(
                    UUID userId, UUID postId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public OrdDtos.OrdCreativeStatus creativeStatus(UUID userId, UUID postId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public OrdDtos.OrdReportRunResponse runReport(
                    UUID userId, OrdDtos.OrdReportRunRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }
        };
    }
}
