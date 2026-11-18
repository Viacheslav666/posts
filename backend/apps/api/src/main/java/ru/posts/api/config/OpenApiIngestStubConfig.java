package ru.posts.api.config;

import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.error.NotImplementedException;
import ru.posts.api.ingest.IngestService;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.ingest.IngestDtos;

@Configuration
@Profile("openapi")
public class OpenApiIngestStubConfig {

    @Bean
    IngestService ingestService() {
        return new IngestService() {
            @Override
            public CursorPage.GrabberRulePage listRules(
                    UUID userId, UUID workspaceId, UUID projectId, IngestDtos.RuleType type, CursorPage.Params page) {
                return new CursorPage.GrabberRulePage(List.of(), null, false);
            }

            @Override
            public IngestDtos.GrabberRule createRule(
                    UUID userId,
                    UUID workspaceId,
                    UUID projectId,
                    IngestDtos.GrabberRuleCreate request,
                    String idempotencyKey,
                    ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public IngestDtos.GrabberRule getRule(UUID userId, UUID ruleId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public IngestDtos.GrabberRule patchRule(
                    UUID userId, UUID ruleId, IngestDtos.GrabberRulePatch request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public IngestDtos.GrabberRule activate(UUID userId, UUID ruleId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public IngestDtos.GrabberRule pause(UUID userId, UUID ruleId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public IngestDtos.GrabberRule resume(UUID userId, UUID ruleId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public IngestDtos.GrabberRule archive(UUID userId, UUID ruleId, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public IngestDtos.GrabberRun startRun(
                    UUID userId, UUID ruleId, String idempotencyKey, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public CursorPage.GrabberRunPage listRuns(UUID userId, UUID ruleId, CursorPage.Params page) {
                return new CursorPage.GrabberRunPage(List.of(), null, false);
            }

            @Override
            public CursorPage.GrabberItemPage listItems(
                    UUID userId, UUID ruleId, IngestDtos.ItemStatus status, CursorPage.Params page) {
                return new CursorPage.GrabberItemPage(List.of(), null, false);
            }

            @Override
            public IngestDtos.GrabberItem getItem(UUID userId, UUID itemId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public IngestDtos.GrabberItem approve(
                    UUID userId,
                    UUID itemId,
                    IngestDtos.GrabberItemApproveRequest request,
                    String idempotencyKey,
                    ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public IngestDtos.GrabberItem reject(
                    UUID userId, UUID itemId, IngestDtos.GrabberItemRejectRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public IngestDtos.GrabberItem editAndApprove(
                    UUID userId,
                    UUID itemId,
                    IngestDtos.GrabberItemEditApproveRequest request,
                    String idempotencyKey,
                    ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public IngestDtos.GrabberBulkModerationResponse bulk(
                    UUID userId, IngestDtos.GrabberBulkModerationRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public IngestDtos.IngestPreviewResponse preview(
                    UUID userId, UUID workspaceId, IngestDtos.IngestPreviewRequest request) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public IngestDtos.GrabberRule cloneConfig(
                    UUID userId, UUID ruleId, IngestDtos.GrabberCloneConfigRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public IngestDtos.GrabberItemsExport exportItems(
                    UUID userId, UUID ruleId, IngestDtos.ItemStatus status, IngestDtos.ExportFormat format) {
                throw new NotImplementedException("openapi");
            }
        };
    }
}
