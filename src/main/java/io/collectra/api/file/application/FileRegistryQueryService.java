package io.collectra.api.file.application;

import io.collectra.api.file.domain.FileCategory;
import io.collectra.api.file.domain.FileStatus;
import io.collectra.api.file.domain.StoredFile;
import io.collectra.api.file.infrastructure.persistence.StoredFileRepository;
import io.collectra.api.shared.error.InvalidRequestException;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileRegistryQueryService {
    public static final int MAX_SIZE = 200;
    private static final Set<String> SORTS =
            Set.of("createdAt", "originalFilename", "sizeBytes", "status", "category");

    private final StoredFileRepository files;

    public FileRegistryQueryService(StoredFileRepository files) {
        this.files = files;
    }

    @Transactional(readOnly = true)
    public Page<StoredFile> list(
            UUID tenantId,
            FileCategory category,
            FileStatus status,
            UUID projectId,
            String filename,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size,
            String sort) {
        if (page < 0 || size <= 0 || size > MAX_SIZE) {
            throw new InvalidRequestException(
                    "INVALID_PAGE_REQUEST", "Invalid file registry page request");
        }
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new InvalidRequestException(
                    "INVALID_DATE_RANGE", "createdFrom must be before createdTo");
        }
        return files.findAll(
                specification(
                        tenantId, category, status, projectId, filename, createdFrom, createdTo),
                PageRequest.of(page, size, parseSort(sort)));
    }

    private static Specification<StoredFile> specification(
            UUID tenantId,
            FileCategory category,
            FileStatus status,
            UUID projectId,
            String filename,
            Instant createdFrom,
            Instant createdTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (category != null) predicates.add(cb.equal(root.get("category"), category));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (projectId != null) predicates.add(cb.equal(root.get("projectId"), projectId));
            if (filename != null && !filename.isBlank()) {
                String pattern = "%" + filename.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("originalFilename")), pattern));
            }
            if (createdFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Sort parseSort(String value) {
        String normalized = value == null || value.isBlank() ? "createdAt,desc" : value.trim();
        String[] parts = normalized.split(",", -1);
        if (parts.length != 2 || !SORTS.contains(parts[0].trim())) {
            throw new InvalidRequestException(
                    "INVALID_SORT", "Unsupported file registry sort field");
        }
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(parts[1].trim());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(
                    "INVALID_SORT", "Unsupported file registry sort direction");
        }
        return Sort.by(direction, parts[0].trim()).and(Sort.by(direction, "id"));
    }
}
