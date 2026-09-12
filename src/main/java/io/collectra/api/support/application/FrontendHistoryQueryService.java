package io.collectra.api.support.application;

import io.collectra.api.shared.error.InvalidRequestException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FrontendHistoryQueryService {
    public static final int MAX_SIZE = 200;

    private final NamedParameterJdbcTemplate jdbc;

    public FrontendHistoryQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<ImportItem> imports(
            UUID tenantId,
            String status,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size) {
        validatePage(page, size);
        validateRange(createdFrom, createdTo);
        StringBuilder where = new StringBuilder(" WHERE tenant_id = :tenantId");
        Map<String, Object> params = baseParams(tenantId, page, size);
        if (status != null && !status.isBlank()) {
            where.append(" AND status = :status");
            params.put("status", status.trim().toUpperCase(java.util.Locale.ROOT));
        }
        appendInstantRange(where, params, createdFrom, createdTo);
        long total = count("SELECT COUNT(*) FROM import_batches" + where, params);
        List<ImportItem> items =
                jdbc.query(
                        """
                        SELECT id, mapping_profile_version_id, template_version_id, status,
                               document_count, error_code, failed_at, created_at, updated_at
                        FROM import_batches
                        """
                                + where
                                + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset",
                        params,
                        (rs, rowNum) ->
                                new ImportItem(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("mapping_profile_version_id", UUID.class),
                                        rs.getObject("template_version_id", UUID.class),
                                        rs.getString("status"),
                                        rs.getInt("document_count"),
                                        rs.getString("error_code"),
                                        instant(rs, "failed_at"),
                                        instant(rs, "created_at"),
                                        instant(rs, "updated_at")));
        return page(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public ImportItem importDetail(UUID tenantId, UUID importId) {
        List<ImportItem> items =
                jdbc.query(
                        """
                        SELECT id, mapping_profile_version_id, template_version_id, status,
                               document_count, error_code, failed_at, created_at, updated_at
                        FROM import_batches
                        WHERE tenant_id = :tenantId AND id = :id
                        """,
                        Map.of("tenantId", tenantId, "id", importId),
                        (rs, rowNum) ->
                                new ImportItem(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("mapping_profile_version_id", UUID.class),
                                        rs.getObject("template_version_id", UUID.class),
                                        rs.getString("status"),
                                        rs.getInt("document_count"),
                                        rs.getString("error_code"),
                                        instant(rs, "failed_at"),
                                        instant(rs, "created_at"),
                                        instant(rs, "updated_at")));
        if (items.isEmpty()) {
            throw new NoSuchElementException("Import not found");
        }
        return items.get(0);
    }

    @Transactional(readOnly = true)
    public PageResponse<ImportErrorItem> importErrors(
            UUID tenantId, UUID importId, int page, int size) {
        validatePage(page, size);
        ImportItem batch = importDetail(tenantId, importId);
        if (batch.errorCode() == null) {
            return page(List.of(), page, size, 0);
        }
        List<ImportErrorItem> items =
                page == 0
                        ? List.of(
                                new ImportErrorItem(
                                        batch.id(),
                                        batch.errorCode(),
                                        "Import processing failed",
                                        batch.failedAt()))
                        : List.of();
        return page(items, page, size, 1);
    }

    @Transactional(readOnly = true)
    public PageResponse<GeneratedDocumentItem> generatedDocuments(
            UUID tenantId,
            String format,
            UUID generationJobId,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size) {
        validatePage(page, size);
        validateRange(createdFrom, createdTo);
        StringBuilder where = new StringBuilder(" WHERE gd.tenant_id = :tenantId");
        Map<String, Object> params = baseParams(tenantId, page, size);
        if (format != null && !format.isBlank()) {
            where.append(" AND gd.format = :format");
            params.put("format", format.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (generationJobId != null) {
            where.append(" AND gd.generation_job_id = :generationJobId");
            params.put("generationJobId", generationJobId);
        }
        if (createdFrom != null) {
            where.append(" AND gd.created_at >= :createdFrom");
            params.put("createdFrom", createdFrom);
        }
        if (createdTo != null) {
            where.append(" AND gd.created_at <= :createdTo");
            params.put("createdTo", createdTo);
        }
        long total =
                count(
                        "SELECT COUNT(*) FROM generated_documents gd JOIN generation_jobs gj ON gj.id = gd.generation_job_id AND gj.tenant_id = gd.tenant_id"
                                + where,
                        params);
        List<GeneratedDocumentItem> items =
                jdbc.query(
                        """
                        SELECT gd.id,
                               gd.generation_job_id,
                               gd.format,
                               gd.media_type,
                               gd.size_bytes,
                               gd.sha256,
                               gd.created_at,
                               gj.status AS generation_status,
                               gj.error_code AS generation_error_code
                        FROM generated_documents gd
                        JOIN generation_jobs gj
                          ON gj.id = gd.generation_job_id
                         AND gj.tenant_id = gd.tenant_id
                        """
                                + where
                                + " ORDER BY gd.created_at DESC, gd.id DESC LIMIT :limit OFFSET :offset",
                        params,
                        (rs, rowNum) -> generatedDocumentItem(rs));
        return page(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public GeneratedDocumentItem generatedDocument(UUID tenantId, UUID documentId) {
        List<GeneratedDocumentItem> items =
                jdbc.query(
                        """
                        SELECT gd.id,
                               gd.generation_job_id,
                               gd.format,
                               gd.media_type,
                               gd.size_bytes,
                               gd.sha256,
                               gd.created_at,
                               gj.status AS generation_status,
                               gj.error_code AS generation_error_code
                        FROM generated_documents gd
                        JOIN generation_jobs gj
                          ON gj.id = gd.generation_job_id
                         AND gj.tenant_id = gd.tenant_id
                        WHERE gd.tenant_id = :tenantId AND gd.id = :id
                        """,
                        Map.of("tenantId", tenantId, "id", documentId),
                        (rs, rowNum) -> generatedDocumentItem(rs));
        if (items.isEmpty()) {
            throw new NoSuchElementException("Generated document not found");
        }
        return items.get(0);
    }

    private void appendInstantRange(
            StringBuilder where,
            Map<String, Object> params,
            Instant createdFrom,
            Instant createdTo) {
        if (createdFrom != null) {
            where.append(" AND created_at >= :createdFrom");
            params.put("createdFrom", createdFrom);
        }
        if (createdTo != null) {
            where.append(" AND created_at <= :createdTo");
            params.put("createdTo", createdTo);
        }
    }

    private Map<String, Object> baseParams(UUID tenantId, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", tenantId);
        params.put("limit", size);
        params.put("offset", (long) page * size);
        return params;
    }

    private long count(String sql, Map<String, Object> params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    private static GeneratedDocumentItem generatedDocumentItem(java.sql.ResultSet rs)
            throws java.sql.SQLException {
        return new GeneratedDocumentItem(
                rs.getObject("id", UUID.class),
                rs.getObject("generation_job_id", UUID.class),
                rs.getString("format"),
                rs.getString("media_type"),
                rs.getLong("size_bytes"),
                rs.getString("sha256"),
                rs.getString("generation_status"),
                rs.getString("generation_error_code"),
                instant(rs, "created_at"));
    }

    private static Instant instant(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new InvalidRequestException("INVALID_REQUEST", "Invalid pagination parameters");
        }
    }

    private static void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException(
                    "INVALID_RANGE", "createdFrom must not be after createdTo");
        }
    }

    private static <T> PageResponse<T> page(List<T> items, int page, int size, long total) {
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new PageResponse<>(items, page, size, total, totalPages, page + 1 < totalPages);
    }

    public record PageResponse<T>(
            List<T> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {}

    public record ImportItem(
            UUID id,
            UUID mappingProfileVersionId,
            UUID templateVersionId,
            String status,
            int documentCount,
            String errorCode,
            Instant failedAt,
            Instant createdAt,
            Instant updatedAt) {}

    public record ImportErrorItem(UUID importId, String code, String detail, Instant failedAt) {}

    public record GeneratedDocumentItem(
            UUID id,
            UUID generationJobId,
            String format,
            String mediaType,
            long sizeBytes,
            String sha256,
            String status,
            String errorCode,
            Instant createdAt) {}
}
