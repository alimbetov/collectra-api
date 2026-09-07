package io.collectra.api.tenant.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "tenants")
public class Tenant extends AuditableEntity {
    @Id private UUID id;
    @Column(nullable=false, unique=true, length=80) private String slug;
    @Column(nullable=false, length=200) private String name;
    @Column(nullable=false, length=40) private String status;
    protected Tenant() {}
    public Tenant(String slug, String name) { this.id=UUID.randomUUID(); this.slug=slug; this.name=name; this.status="ACTIVE"; }
    public UUID getId(){return id;} public String getSlug(){return slug;} public String getName(){return name;}
}
