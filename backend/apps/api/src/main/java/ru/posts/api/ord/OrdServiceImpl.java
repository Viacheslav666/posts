package ru.posts.api.ord;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.channel.WorkspaceTokenVault;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.ord.OrdDtos;
import ru.posts.api.workspace.AuditRecorder;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.common.uuid.UuidV7;
import ru.posts.ord.api.CreativeRegistrationResult;
import ru.posts.ord.api.OrdApiException;
import ru.posts.ord.api.OrdContractRequest;
import ru.posts.ord.api.OrdCreativeRequest;
import ru.posts.ord.api.OrdCredentials;
import ru.posts.ord.api.OrdErrorClass;
import ru.posts.ord.api.OrdPersonRequest;
import ru.posts.ord.api.OrdProvider;
import ru.posts.persistence.entity.OrdConnectionEntity;
import ru.posts.persistence.entity.OrdContractEntity;
import ru.posts.persistence.entity.OrdContractorEntity;
import ru.posts.persistence.entity.OrdCreativeEntity;
import ru.posts.persistence.entity.OrdDictionaryKktuEntity;
import ru.posts.persistence.entity.OrdDictionaryOksmEntity;
import ru.posts.persistence.entity.OrdReportRunEntity;
import ru.posts.persistence.entity.PostEntity;
import ru.posts.persistence.repository.OrdConnectionRepository;
import ru.posts.persistence.repository.OrdContractRepository;
import ru.posts.persistence.repository.OrdContractorRepository;
import ru.posts.persistence.repository.OrdCreativeRepository;
import ru.posts.persistence.repository.OrdDictionaryKktuRepository;
import ru.posts.persistence.repository.OrdDictionaryOksmRepository;
import ru.posts.persistence.repository.OrdReportRunRepository;
import ru.posts.persistence.repository.PostRepository;

@Service
@Profile("!openapi")
@Transactional
public class OrdServiceImpl implements OrdService {

    private final WorkspaceAccessService access;
    private final WorkspaceTokenVault tokenVault;
    private final AuditRecorder auditRecorder;
    private final OrdProviderRegistry providerRegistry;
    private final OrdConnectionRepository connectionRepository;
    private final OrdContractorRepository contractorRepository;
    private final OrdContractRepository contractRepository;
    private final OrdCreativeRepository creativeRepository;
    private final OrdDictionaryKktuRepository kktuRepository;
    private final OrdDictionaryOksmRepository oksmRepository;
    private final OrdReportRunRepository reportRunRepository;
    private final PostRepository postRepository;
    private final ObjectMapper objectMapper;

    public OrdServiceImpl(
            WorkspaceAccessService access,
            WorkspaceTokenVault tokenVault,
            AuditRecorder auditRecorder,
            OrdProviderRegistry providerRegistry,
            OrdConnectionRepository connectionRepository,
            OrdContractorRepository contractorRepository,
            OrdContractRepository contractRepository,
            OrdCreativeRepository creativeRepository,
            OrdDictionaryKktuRepository kktuRepository,
            OrdDictionaryOksmRepository oksmRepository,
            OrdReportRunRepository reportRunRepository,
            PostRepository postRepository,
            ObjectMapper objectMapper) {
        this.access = access;
        this.tokenVault = tokenVault;
        this.auditRecorder = auditRecorder;
        this.providerRegistry = providerRegistry;
        this.connectionRepository = connectionRepository;
        this.contractorRepository = contractorRepository;
        this.contractRepository = contractRepository;
        this.creativeRepository = creativeRepository;
        this.kktuRepository = kktuRepository;
        this.oksmRepository = oksmRepository;
        this.reportRunRepository = reportRunRepository;
        this.postRepository = postRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.OrdConnectionPage listConnections(UUID userId, UUID workspaceId) {
        access.requirePermission(workspaceId, userId, "ord.view");
        List<OrdDtos.OrdConnection> items =
                connectionRepository.findByWorkspaceIdOrderByUpdatedAtDesc(workspaceId).stream()
                        .map(this::toConnectionDto)
                        .toList();
        return new CursorPage.OrdConnectionPage(items, null, false);
    }

    @Override
    public OrdDtos.OrdConnection upsertConnection(
            UUID userId,
            UUID workspaceId,
            OrdDtos.OrdProviderCode provider,
            OrdDtos.UpsertOrdConnectionRequest request,
            ClientRequestContext ctx) {
        access.requirePermission(workspaceId, userId, "ord.connection.manage");
        if (!Boolean.TRUE.equals(request.responsibilityAccepted())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.ORD_VALIDATION_FAILED,
                    "responsibilityAccepted must be true");
        }
        if (request.publisherInn() != null
                && !request.publisherInn().isBlank()
                && !InnValidator.isValid(request.publisherInn())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.AD_INN_INVALID, "INN must be 10 or 12 digits");
        }

        Instant now = Instant.now();
        OrdConnectionEntity entity = connectionRepository
                .findByWorkspaceIdAndProvider(workspaceId, provider.name())
                .orElseGet(OrdConnectionEntity::new);
        boolean created = entity.getId() == null;
        if (created) {
            entity.setId(UuidV7.generate());
            entity.setWorkspaceId(workspaceId);
            entity.setProvider(provider.name());
            entity.setCreatedAt(now);
            entity.setStatus("draft");
        }

        String apiKey = request.apiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            entity.setApiKeyEnc(tokenVault.seal(workspaceId, apiKey.trim()));
        } else if (created || entity.getApiKeyEnc() == null || entity.getApiKeyEnc().length == 0) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.ORD_VALIDATION_FAILED, "apiKey is required");
        }

        entity.setPublisherInn(blankToNull(request.publisherInn()));
        entity.setPublisherName(blankToNull(request.publisherName()));
        entity.setOrgType(blankToNull(request.orgType()));
        entity.setPhone(blankToNull(request.phone()));
        entity.setRoles(request.roles() == null ? List.of() : List.copyOf(request.roles()));
        if (Boolean.TRUE.equals(request.isDefault())) {
            clearDefault(workspaceId, provider.name());
            entity.setDefault(true);
        } else if (request.isDefault() != null) {
            entity.setDefault(false);
        }
        entity.setUpdatedAt(now);
        connectionRepository.save(entity);

        auditRecorder.record(
                workspaceId,
                userId,
                "ord.connection.updated",
                "ord_connection",
                entity.getId(),
                Map.of("provider", provider.name(), "created", created),
                ctx == null ? null : ctx.ip());
        return toConnectionDto(entity);
    }

    @Override
    public OrdDtos.OrdConnectionTestResponse testConnection(
            UUID userId, UUID workspaceId, OrdDtos.OrdProviderCode provider, ClientRequestContext ctx) {
        access.requirePermission(workspaceId, userId, "ord.connection.manage");
        OrdConnectionEntity entity = connectionRepository
                .findByWorkspaceIdAndProvider(workspaceId, provider.name())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ProblemCodes.ORD_NOT_CONFIGURED,
                        "ORD connection not configured"));
        String apiKey = tokenVault.unseal(workspaceId, entity.getApiKeyEnc());
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.ORD_NOT_CONFIGURED, "ORD API key missing");
        }
        OrdProvider ordProvider = providerRegistry.require(provider.name());
        try {
            ordProvider.testConnection(new OrdCredentials(apiKey));
            entity.setStatus("active");
            entity.setLastError(null);
            entity.setUpdatedAt(Instant.now());
            connectionRepository.save(entity);
            return new OrdDtos.OrdConnectionTestResponse(true, "active", "ok");
        } catch (OrdApiException ex) {
            entity.setStatus("error");
            entity.setLastError(safeMessage(ex));
            entity.setUpdatedAt(Instant.now());
            connectionRepository.save(entity);
            String code = mapOrdError(ex.errorClass());
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, safeMessage(ex));
        } catch (RuntimeException ex) {
            entity.setStatus("error");
            entity.setLastError(safeMessage(ex));
            entity.setUpdatedAt(Instant.now());
            connectionRepository.save(entity);
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.ORD_PROVIDER_UNAVAILABLE, "ORD provider unavailable");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.OrdContractorPage listContractors(UUID userId, UUID workspaceId) {
        access.requirePermission(workspaceId, userId, "ord.view");
        List<OrdDtos.OrdContractor> items =
                contractorRepository.findByWorkspaceIdOrderByUpdatedAtDesc(workspaceId).stream()
                        .map(this::toContractorDto)
                        .toList();
        return new CursorPage.OrdContractorPage(items, null, false);
    }

    @Override
    public OrdDtos.OrdContractor createContractor(
            UUID userId, UUID workspaceId, OrdDtos.CreateOrdContractorRequest request, ClientRequestContext ctx) {
        access.requirePermission(workspaceId, userId, "ord.contractors.manage");
        if (!InnValidator.isValid(request.inn())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.AD_INN_INVALID, "INN must be 10 or 12 digits");
        }
        String kpp = request.kpp() == null ? "" : request.kpp().trim();
        contractorRepository
                .findByWorkspaceIdAndInnAndKpp(workspaceId, request.inn().trim(), kpp)
                .ifPresent(existing -> {
                    throw new ApiException(
                            HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Contractor with same INN/KPP exists");
                });

        Instant now = Instant.now();
        OrdContractorEntity entity = new OrdContractorEntity();
        entity.setId(UuidV7.generate());
        entity.setWorkspaceId(workspaceId);
        entity.setInn(request.inn().trim());
        entity.setKpp(kpp);
        entity.setName(request.name().trim());
        entity.setOrgType(request.orgType().trim());
        entity.setCountryOksm(
                blankToNull(request.countryOksm()) == null
                        ? "643"
                        : request.countryOksm().trim());
        entity.setPhone(blankToNull(request.phone()));
        entity.setAddress(blankToNull(request.address()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        contractorRepository.save(entity);

        auditRecorder.record(
                workspaceId,
                userId,
                "ord.contractor.upserted",
                "ord_contractor",
                entity.getId(),
                Map.of("inn", entity.getInn()),
                ctx == null ? null : ctx.ip());
        return toContractorDto(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public OrdDtos.OrdContractor getContractor(UUID userId, UUID workspaceId, UUID contractorId) {
        access.requirePermission(workspaceId, userId, "ord.view");
        return toContractorDto(requireContractor(workspaceId, contractorId));
    }

    @Override
    public OrdDtos.OrdContractor updateContractor(
            UUID userId,
            UUID workspaceId,
            UUID contractorId,
            OrdDtos.UpdateOrdContractorRequest request,
            ClientRequestContext ctx) {
        access.requirePermission(workspaceId, userId, "ord.contractors.manage");
        OrdContractorEntity entity = requireContractor(workspaceId, contractorId);
        if (request.inn() != null) {
            if (!InnValidator.isValid(request.inn())) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.AD_INN_INVALID, "INN must be 10 or 12 digits");
            }
            entity.setInn(request.inn().trim());
        }
        if (request.kpp() != null) {
            entity.setKpp(request.kpp().trim());
        }
        if (request.name() != null) {
            entity.setName(request.name().trim());
        }
        if (request.orgType() != null) {
            entity.setOrgType(request.orgType().trim());
        }
        if (request.countryOksm() != null) {
            entity.setCountryOksm(request.countryOksm().trim());
        }
        if (request.phone() != null) {
            entity.setPhone(blankToNull(request.phone()));
        }
        if (request.address() != null) {
            entity.setAddress(blankToNull(request.address()));
        }
        entity.setUpdatedAt(Instant.now());
        contractorRepository.save(entity);
        auditRecorder.record(
                workspaceId,
                userId,
                "ord.contractor.upserted",
                "ord_contractor",
                entity.getId(),
                Map.of("inn", entity.getInn()),
                ctx == null ? null : ctx.ip());
        return toContractorDto(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.OrdContractPage listContracts(UUID userId, UUID workspaceId) {
        access.requirePermission(workspaceId, userId, "ord.view");
        List<OrdDtos.OrdContract> items = contractRepository.findByWorkspaceIdOrderByUpdatedAtDesc(workspaceId).stream()
                .map(this::toContractDto)
                .toList();
        return new CursorPage.OrdContractPage(items, null, false);
    }

    @Override
    public OrdDtos.OrdContract createContract(
            UUID userId, UUID workspaceId, OrdDtos.CreateOrdContractRequest request, ClientRequestContext ctx) {
        access.requirePermission(workspaceId, userId, "ord.contractors.manage");
        requireContractor(workspaceId, request.contractorId());
        contractRepository
                .findByWorkspaceIdAndProviderAndExternalId(
                        workspaceId, request.provider().name(), request.externalId())
                .ifPresent(existing -> {
                    throw new ApiException(
                            HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Contract externalId already exists");
                });

        Instant now = Instant.now();
        OrdContractEntity entity = new OrdContractEntity();
        entity.setId(UuidV7.generate());
        entity.setWorkspaceId(workspaceId);
        entity.setContractorId(request.contractorId());
        entity.setExternalId(request.externalId().trim());
        entity.setSerial(blankToNull(request.serial()));
        entity.setDateStart(request.dateStart());
        entity.setDateEnd(request.dateEnd());
        entity.setAmount(request.amount());
        entity.setVatRate(blankToNull(request.vatRate()) == null ? "without_vat" : request.vatRate());
        entity.setProvider(request.provider().name());
        entity.setSelfPromo(Boolean.TRUE.equals(request.selfPromo()));
        entity.setStatus("draft");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        contractRepository.save(entity);
        return toContractDto(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrdDtos.DictionaryKktuItem> searchKktu(String q) {
        List<OrdDictionaryKktuEntity> rows;
        if (q == null || q.isBlank()) {
            rows = kktuRepository.findAll();
        } else {
            LinkedHashSet<OrdDictionaryKktuEntity> set = new LinkedHashSet<>();
            set.addAll(kktuRepository.findByCodeContainingIgnoreCase(q.trim()));
            set.addAll(kktuRepository.findByNameContainingIgnoreCase(q.trim()));
            rows = List.copyOf(set);
        }
        return rows.stream()
                .filter(OrdDictionaryKktuEntity::isActive)
                .map(e -> new OrdDtos.DictionaryKktuItem(e.getCode(), e.getName(), e.getParentCode(), e.isActive()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrdDtos.DictionaryOksmItem> searchOksm(String q) {
        List<OrdDictionaryOksmEntity> rows;
        if (q == null || q.isBlank()) {
            rows = oksmRepository.findAll();
        } else {
            LinkedHashSet<OrdDictionaryOksmEntity> set = new LinkedHashSet<>();
            set.addAll(oksmRepository.findByCodeContainingIgnoreCase(q.trim()));
            set.addAll(oksmRepository.findByNameContainingIgnoreCase(q.trim()));
            rows = List.copyOf(set);
        }
        return rows.stream()
                .filter(OrdDictionaryOksmEntity::isActive)
                .map(e -> new OrdDtos.DictionaryOksmItem(e.getCode(), e.getName(), e.getAlpha2(), e.isActive()))
                .toList();
    }

    @Override
    public OrdDtos.OrdRegisterCreativeResponse registerCreative(UUID userId, UUID postId, ClientRequestContext ctx) {
        PostEntity post =
                postRepository.findByIdAndDeletedAtIsNull(postId).orElseThrow(WorkspaceAccessService::notFound);
        access.requirePermission(post.getWorkspaceId(), userId, "ord.view");
        if (!post.isAd()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.ORD_VALIDATION_FAILED, "Post is not marked as ad");
        }

        OrdConnectionEntity connection = requireActiveConnection(post.getWorkspaceId());
        OrdDtos.AdMarking marking = readMarking(post);
        AdMarkingValidator.requireValidWhenAd(true, marking);

        String providerCode = connection.getProvider();
        // Prefer already-linked registered creative (version bump on post must not mint a new erid).
        if (post.getOrdCreativeId() != null) {
            Optional<OrdCreativeEntity> linked = creativeRepository.findById(post.getOrdCreativeId());
            if (linked.isPresent()
                    && linked.get().getErid() != null
                    && !linked.get().getErid().isBlank()
                    && "registered".equals(linked.get().getStatus())) {
                return toRegisterResponse(linked.get());
            }
        }
        String externalId = "creative:" + post.getId() + ":" + post.getVersion();
        Optional<OrdCreativeEntity> existing = creativeRepository.findByWorkspaceIdAndProviderAndExternalId(
                post.getWorkspaceId(), providerCode, externalId);
        if (existing.isPresent()
                && existing.get().getErid() != null
                && !existing.get().getErid().isBlank()
                && "registered".equals(existing.get().getStatus())) {
            OrdCreativeEntity creative = existing.get();
            post.setOrdCreativeId(creative.getId());
            postRepository.save(post);
            return toRegisterResponse(creative);
        }

        String apiKey = tokenVault.unseal(post.getWorkspaceId(), connection.getApiKeyEnc());
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.ORD_NOT_CONFIGURED, "ORD API key missing");
        }
        OrdCredentials credentials = new OrdCredentials(apiKey);
        OrdProvider provider = providerRegistry.require(providerCode);

        OrdContractorEntity contractor = resolveContractor(post.getWorkspaceId(), marking);
        String personExternalId = "person:" + contractor.getId();
        String contractExternalId = "contract:" + post.getId() + ":" + post.getVersion();

        try {
            Map<String, String> foreign = new LinkedHashMap<>();
            if (marking.foreignInn() != null) {
                foreign.put("foreign_inn", marking.foreignInn());
            }
            if (marking.foreignRegNumber() != null) {
                foreign.put("foreign_reg_number", marking.foreignRegNumber());
            }
            if (marking.foreignEpayment() != null) {
                foreign.put("foreign_epayment", marking.foreignEpayment());
            }
            provider.upsertPerson(
                    credentials,
                    new OrdPersonRequest(
                            personExternalId,
                            marking.advertiserName(),
                            marking.orgType(),
                            marking.inn(),
                            marking.kpp() == null ? "" : marking.kpp(),
                            marking.phone(),
                            marking.oksmCountry() == null ? "643" : marking.oksmCountry(),
                            marking.roles() == null ? List.of("advertiser") : marking.roles(),
                            foreign));

            boolean selfPromo = Boolean.TRUE.equals(marking.selfPromo());
            OrdContractEntity contract = upsertLocalContract(
                    post.getWorkspaceId(), contractor.getId(), providerCode, contractExternalId, marking, selfPromo);
            if (!selfPromo) {
                provider.upsertContract(
                        credentials,
                        new OrdContractRequest(
                                contractExternalId,
                                personExternalId,
                                personExternalId,
                                "service",
                                marking.contractSerial(),
                                marking.contractDate(),
                                marking.contractDateEnd(),
                                marking.contractAmount() == null ? marking.amount() : marking.contractAmount(),
                                marking.vatRate() == null ? "without_vat" : marking.vatRate(),
                                false,
                                null,
                                null,
                                null,
                                List.of()));
                contract.setStatus("synced");
                contractRepository.save(contract);
            }

            OrdCreativeEntity creative = existing.orElseGet(OrdCreativeEntity::new);
            if (creative.getId() == null) {
                creative.setId(UuidV7.generate());
                creative.setWorkspaceId(post.getWorkspaceId());
                creative.setExternalId(externalId);
                creative.setProvider(providerCode);
                creative.setCreatedAt(Instant.now());
            }
            creative.setPostId(post.getId());
            creative.setConnectionId(connection.getId());
            creative.setContractId(selfPromo ? null : contract.getId());
            creative.setAdvertiserName(marking.advertiserName());
            creative.setAdvertiserInn(marking.inn());
            creative.setKktu(
                    marking.kktuCodes() == null
                            ? new String[0]
                            : marking.kktuCodes().toArray(String[]::new));
            if (marking.displayPeriodDays() != null) {
                creative.setDisplayPeriodDays(marking.displayPeriodDays());
            }
            if (marking.autoDeleteAfterPeriod() != null) {
                creative.setAutoDeleteAfterPeriod(marking.autoDeleteAfterPeriod());
            }
            creative.setStatus("registering");
            creative.setLastError(null);
            creative.setUpdatedAt(Instant.now());
            creativeRepository.save(creative);

            CreativeRegistrationResult result = provider.registerCreative(
                    credentials,
                    new OrdCreativeRequest(
                            externalId,
                            selfPromo ? List.of() : List.of(contractExternalId),
                            marking.advertiserName(),
                            marking.description(),
                            marking.kktuCodes(),
                            List.of(),
                            "text_block",
                            selfPromo,
                            selfPromo ? personExternalId : null,
                            List.of(),
                            null,
                            null,
                            null,
                            List.of()));

            if (result.erid() == null || result.erid().isBlank()) {
                creative.setStatus("error");
                creative.setLastError("erid missing");
                creativeRepository.save(creative);
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.ORD_ERID_MISSING, "ORD returned empty erid");
            }

            Instant now = Instant.now();
            creative.setErid(result.erid());
            creative.setStatus("registered");
            creative.setRegisteredAt(now);
            creative.setUpdatedAt(now);
            creativeRepository.save(creative);

            Map<String, Object> payload =
                    post.getAdPayload() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(post.getAdPayload());
            payload.put("erid", result.erid());
            post.setAdPayload(payload);
            post.setOrdCreativeId(creative.getId());
            post.setUpdatedAt(now);
            postRepository.save(post);

            auditRecorder.record(
                    post.getWorkspaceId(),
                    userId,
                    "ord.creative.registered",
                    "ord_creative",
                    creative.getId(),
                    Map.of("postId", post.getId().toString(), "eridPresent", true),
                    ctx == null ? null : ctx.ip());
            return toRegisterResponse(creative);
        } catch (ApiException ex) {
            throw ex;
        } catch (OrdApiException ex) {
            auditRecorder.record(
                    post.getWorkspaceId(),
                    userId,
                    "ord.creative.failed",
                    "post",
                    post.getId(),
                    Map.of("errorClass", ex.errorClass().name()),
                    ctx == null ? null : ctx.ip());
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, mapOrdError(ex.errorClass()), safeMessage(ex));
        } catch (RuntimeException ex) {
            auditRecorder.record(
                    post.getWorkspaceId(),
                    userId,
                    "ord.creative.failed",
                    "post",
                    post.getId(),
                    Map.of("error", "provider"),
                    ctx == null ? null : ctx.ip());
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.ORD_PROVIDER_UNAVAILABLE, "ORD provider unavailable");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrdDtos.OrdCreativeStatus creativeStatus(UUID userId, UUID postId) {
        PostEntity post =
                postRepository.findByIdAndDeletedAtIsNull(postId).orElseThrow(WorkspaceAccessService::notFound);
        access.requirePermission(post.getWorkspaceId(), userId, "ord.view");
        OrdCreativeEntity creative = null;
        if (post.getOrdCreativeId() != null) {
            creative = creativeRepository.findById(post.getOrdCreativeId()).orElse(null);
        }
        if (creative == null) {
            List<OrdCreativeEntity> byPost = creativeRepository.findByPostId(postId);
            if (!byPost.isEmpty()) {
                creative = byPost.get(0);
            }
        }
        if (creative == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Creative not registered");
        }
        return new OrdDtos.OrdCreativeStatus(
                creative.getId(),
                creative.getPostId(),
                creative.getExternalId(),
                creative.getErid(),
                OrdDtos.OrdProviderCode.valueOf(creative.getProvider()),
                creative.getStatus(),
                creative.getLastError(),
                creative.getRegisteredAt());
    }

    @Override
    public OrdDtos.OrdReportRunResponse runReport(
            UUID userId, OrdDtos.OrdReportRunRequest request, ClientRequestContext ctx) {
        access.requirePermission(request.workspaceId(), userId, "ord.reports.run");
        List<OrdConnectionEntity> connections =
                connectionRepository.findByWorkspaceIdOrderByUpdatedAtDesc(request.workspaceId()).stream()
                        .filter(c -> "active".equals(c.getStatus()))
                        .filter(c -> request.provider() == null
                                || request.provider().name().equals(c.getProvider()))
                        .toList();
        if (connections.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ProblemCodes.ORD_NOT_CONFIGURED,
                    "No active ORD connection for report");
        }

        OrdConnectionEntity connection = connections.get(0);
        Optional<OrdReportRunEntity> existing =
                reportRunRepository.findByWorkspaceIdAndProviderAndPeriodYearAndPeriodMonth(
                        request.workspaceId(), connection.getProvider(), request.year(), request.month());
        if (existing.isPresent() && "succeeded".equals(existing.get().getStatus())) {
            OrdReportRunEntity run = existing.get();
            return new OrdDtos.OrdReportRunResponse(
                    run.getId(),
                    run.getStatus(),
                    run.getInvoicesCount(),
                    run.getStatisticsCount(),
                    run.getPeriodYear(),
                    run.getPeriodMonth());
        }

        OrdReportRunEntity run = existing.orElseGet(OrdReportRunEntity::new);
        if (run.getId() == null) {
            run.setId(UuidV7.generate());
            run.setWorkspaceId(request.workspaceId());
            run.setProvider(connection.getProvider());
            run.setPeriodYear(request.year());
            run.setPeriodMonth(request.month());
            run.setStartedAt(Instant.now());
        }
        run.setStatus("succeeded");
        run.setInvoicesCount(0);
        run.setStatisticsCount(0);
        run.setFinishedAt(Instant.now());
        run.setLastError(null);
        reportRunRepository.save(run);

        auditRecorder.record(
                request.workspaceId(),
                userId,
                "ord.report.submitted",
                "ord_report_run",
                run.getId(),
                Map.of("year", request.year(), "month", request.month()),
                ctx == null ? null : ctx.ip());
        return new OrdDtos.OrdReportRunResponse(
                run.getId(),
                run.getStatus(),
                run.getInvoicesCount(),
                run.getStatisticsCount(),
                run.getPeriodYear(),
                run.getPeriodMonth());
    }

    /** Active default connection, else first active — for schedule guards. */
    public Optional<OrdConnectionEntity> findActiveConnection(UUID workspaceId) {
        List<OrdConnectionEntity> all = connectionRepository.findByWorkspaceIdOrderByUpdatedAtDesc(workspaceId);
        Optional<OrdConnectionEntity> preferred = all.stream()
                .filter(c -> "active".equals(c.getStatus()) && c.isDefault())
                .findFirst();
        if (preferred.isPresent()) {
            return preferred;
        }
        return all.stream().filter(c -> "active".equals(c.getStatus())).findFirst();
    }

    private OrdConnectionEntity requireActiveConnection(UUID workspaceId) {
        return findActiveConnection(workspaceId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.ORD_NOT_CONFIGURED, "No active ORD connection"));
    }

    private OrdContractorEntity resolveContractor(UUID workspaceId, OrdDtos.AdMarking marking) {
        if (marking.contractorId() != null) {
            return requireContractor(workspaceId, marking.contractorId());
        }
        String kpp = marking.kpp() == null ? "" : marking.kpp();
        return contractorRepository
                .findByWorkspaceIdAndInnAndKpp(workspaceId, marking.inn(), kpp)
                .orElseGet(() -> {
                    Instant now = Instant.now();
                    OrdContractorEntity created = new OrdContractorEntity();
                    created.setId(UuidV7.generate());
                    created.setWorkspaceId(workspaceId);
                    created.setInn(marking.inn());
                    created.setKpp(kpp);
                    created.setName(marking.advertiserName());
                    created.setOrgType(marking.orgType() == null ? "juridical" : marking.orgType());
                    created.setCountryOksm(marking.oksmCountry() == null ? "643" : marking.oksmCountry());
                    created.setPhone(marking.phone());
                    created.setAddress(marking.address());
                    created.setCreatedAt(now);
                    created.setUpdatedAt(now);
                    return contractorRepository.save(created);
                });
    }

    private OrdContractEntity upsertLocalContract(
            UUID workspaceId,
            UUID contractorId,
            String provider,
            String externalId,
            OrdDtos.AdMarking marking,
            boolean selfPromo) {
        OrdContractEntity entity = contractRepository
                .findByWorkspaceIdAndProviderAndExternalId(workspaceId, provider, externalId)
                .orElseGet(OrdContractEntity::new);
        Instant now = Instant.now();
        if (entity.getId() == null) {
            entity.setId(UuidV7.generate());
            entity.setWorkspaceId(workspaceId);
            entity.setExternalId(externalId);
            entity.setProvider(provider);
            entity.setCreatedAt(now);
        }
        entity.setContractorId(contractorId);
        entity.setSerial(marking.contractSerial());
        entity.setDateStart(marking.contractDate());
        entity.setDateEnd(marking.contractDateEnd());
        entity.setAmount(marking.contractAmount() == null ? marking.amount() : marking.contractAmount());
        entity.setVatRate(marking.vatRate() == null ? "without_vat" : marking.vatRate());
        entity.setSelfPromo(selfPromo);
        entity.setStatus(selfPromo ? "synced" : "draft");
        entity.setUpdatedAt(now);
        return contractRepository.save(entity);
    }

    private OrdContractorEntity requireContractor(UUID workspaceId, UUID contractorId) {
        return contractorRepository
                .findByIdAndWorkspaceId(contractorId, workspaceId)
                .orElseThrow(WorkspaceAccessService::notFound);
    }

    private void clearDefault(UUID workspaceId, String keepProvider) {
        for (OrdConnectionEntity c : connectionRepository.findByWorkspaceIdOrderByUpdatedAtDesc(workspaceId)) {
            if (c.isDefault() && !c.getProvider().equals(keepProvider)) {
                c.setDefault(false);
                c.setUpdatedAt(Instant.now());
                connectionRepository.save(c);
            }
        }
    }

    private OrdDtos.AdMarking readMarking(PostEntity post) {
        if (post.getAdPayload() == null || post.getAdPayload().isEmpty()) {
            return null;
        }
        return objectMapper.convertValue(post.getAdPayload(), OrdDtos.AdMarking.class);
    }

    private OrdDtos.OrdConnection toConnectionDto(OrdConnectionEntity e) {
        boolean hasKey = e.getApiKeyEnc() != null && e.getApiKeyEnc().length > 1;
        return new OrdDtos.OrdConnection(
                e.getId(),
                e.getWorkspaceId(),
                OrdDtos.OrdProviderCode.valueOf(e.getProvider()),
                e.getPublisherInn(),
                e.getPublisherName(),
                e.getOrgType(),
                e.getPhone(),
                e.getRoles() == null ? List.of() : e.getRoles(),
                e.getStatus(),
                e.isDefault(),
                hasKey,
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private OrdDtos.OrdContractor toContractorDto(OrdContractorEntity e) {
        return new OrdDtos.OrdContractor(
                e.getId(),
                e.getWorkspaceId(),
                e.getInn(),
                e.getKpp(),
                e.getName(),
                e.getOrgType(),
                e.getCountryOksm(),
                e.getPhone(),
                e.getAddress(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private OrdDtos.OrdContract toContractDto(OrdContractEntity e) {
        return new OrdDtos.OrdContract(
                e.getId(),
                e.getWorkspaceId(),
                e.getContractorId(),
                e.getExternalId(),
                e.getSerial(),
                e.getDateStart(),
                e.getDateEnd(),
                e.getAmount(),
                e.getVatRate(),
                OrdDtos.OrdProviderCode.valueOf(e.getProvider()),
                e.isSelfPromo(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private OrdDtos.OrdRegisterCreativeResponse toRegisterResponse(OrdCreativeEntity creative) {
        return new OrdDtos.OrdRegisterCreativeResponse(
                creative.getId(),
                creative.getPostId(),
                creative.getExternalId(),
                creative.getErid(),
                OrdDtos.OrdProviderCode.valueOf(creative.getProvider()),
                creative.getStatus(),
                creative.getLastError(),
                creative.getRegisteredAt());
    }

    private static String mapOrdError(OrdErrorClass errorClass) {
        return switch (errorClass) {
            case VALIDATION -> ProblemCodes.ORD_VALIDATION_FAILED;
            case AUTH -> ProblemCodes.ORD_NOT_CONFIGURED;
            case RETRYABLE, RATE_LIMITED -> ProblemCodes.ORD_PROVIDER_UNAVAILABLE;
            case PERMANENT -> ProblemCodes.ORD_VALIDATION_FAILED;
        };
    }

    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "ORD error";
        }
        // Never echo potential secrets.
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("apikey") || lower.contains("api_key")) {
            return "ORD error";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
