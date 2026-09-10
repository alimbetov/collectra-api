package io.collectra.api.localization.infrastructure;

import io.collectra.api.localization.domain.SupportedLocale;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportedLocaleRepository extends JpaRepository<SupportedLocale, String> {
    List<SupportedLocale> findAllByEnabledTrueOrderBySortOrderAsc();
}
