package io.collectra.api.localization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "supported_locales")
public class SupportedLocale {
    @Id
    @Column(length = 35)
    private String code;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "native_name", nullable = false, length = 100)
    private String nativeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private TextDirection direction;

    @Column(name = "fallback_locale", length = 35)
    private String fallbackLocale;

    @Enumerated(EnumType.STRING)
    @Column(name = "font_profile", nullable = false, length = 32)
    private FontProfileCode fontProfile;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected SupportedLocale() {}

    public String getCode() {
        return code;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getNativeName() {
        return nativeName;
    }

    public TextDirection getDirection() {
        return direction;
    }

    public String getFallbackLocale() {
        return fallbackLocale;
    }

    public FontProfileCode getFontProfile() {
        return fontProfile;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
