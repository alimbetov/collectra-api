package io.collectra.api.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "permissions")
public class Permission {

    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 100) private String code;
    @Column(nullable = false, length = 60) private String module;
    @Column(nullable = false, length = 255) private String description;

    protected Permission() {}

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getModule() { return module; }
    public String getDescription() { return description; }
}
