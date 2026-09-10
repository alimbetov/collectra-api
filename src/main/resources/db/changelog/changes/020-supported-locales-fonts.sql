--liquibase formatted sql

--changeset collectra:020-supported-locales
CREATE TABLE supported_locales (
    code VARCHAR(35) PRIMARY KEY,
    language_code VARCHAR(10) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    native_name VARCHAR(100) NOT NULL,
    direction VARCHAR(3) NOT NULL,
    fallback_locale VARCHAR(35),
    font_profile VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL,
    CONSTRAINT ck_supported_locale_direction CHECK (direction IN ('LTR', 'RTL')),
    CONSTRAINT ck_supported_locale_font_profile CHECK (
        font_profile IN ('LATIN_CYRILLIC', 'ARMENIAN', 'GEORGIAN', 'CJK_SC')
    ),
    CONSTRAINT fk_supported_locale_fallback
        FOREIGN KEY (fallback_locale) REFERENCES supported_locales(code)
);

CREATE INDEX idx_supported_locales_enabled_order
    ON supported_locales(enabled, sort_order, code);

INSERT INTO supported_locales(
    code, language_code, display_name, native_name, direction,
    fallback_locale, font_profile, enabled, sort_order)
VALUES
    ('en', 'en', 'English', 'English', 'LTR', NULL, 'LATIN_CYRILLIC', TRUE, 10),
    ('ru', 'ru', 'Russian', 'Русский', 'LTR', 'en', 'LATIN_CYRILLIC', TRUE, 20),
    ('kk', 'kk', 'Kazakh', 'Қазақша', 'LTR', 'ru', 'LATIN_CYRILLIC', TRUE, 30),
    ('uz', 'uz', 'Uzbek', 'O‘zbekcha', 'LTR', 'ru', 'LATIN_CYRILLIC', TRUE, 40),
    ('ky', 'ky', 'Kyrgyz', 'Кыргызча', 'LTR', 'ru', 'LATIN_CYRILLIC', TRUE, 50),
    ('tg', 'tg', 'Tajik', 'Тоҷикӣ', 'LTR', 'ru', 'LATIN_CYRILLIC', TRUE, 60),
    ('az', 'az', 'Azerbaijani', 'Azərbaycan', 'LTR', 'ru', 'LATIN_CYRILLIC', TRUE, 70),
    ('hy', 'hy', 'Armenian', 'Հայերեն', 'LTR', 'ru', 'ARMENIAN', TRUE, 80),
    ('ka', 'ka', 'Georgian', 'ქართული', 'LTR', 'ru', 'GEORGIAN', TRUE, 90),
    ('be', 'be', 'Belarusian', 'Беларуская', 'LTR', 'ru', 'LATIN_CYRILLIC', TRUE, 100),
    ('uk', 'uk', 'Ukrainian', 'Українська', 'LTR', 'ru', 'LATIN_CYRILLIC', TRUE, 110),
    ('zh-CN', 'zh', 'Chinese (Simplified)', '简体中文', 'LTR', 'en', 'CJK_SC', TRUE, 120);

--changeset collectra:020-template-locale-bcp47
ALTER TABLE template_versions
    ALTER COLUMN locale TYPE VARCHAR(35);
