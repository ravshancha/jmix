package uz.kapitalbank.umida.enums;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

/**
 * Уровень доступа сотрудника к отчёту.
 * <p>
 * NONE в базе не хранится — это отсутствие строки {@code ReportAccess}; значение нужно, чтобы
 * экраны могли единообразно показывать «нет доступа». OWNER выводится из {@code UserReport.owner},
 * SUBDIVISION_OWNER — у сотрудников подразделения-владельца ({@code UserReport.subdivision}) и
 * вложенных в него; ни тот, ни другой отдельной строкой доступа не заводятся.
 */
public enum AccessLevel implements EnumClass<String> {

    NONE("NONE"),
    READ("READ"),
    GRANT("GRANT"),
    OWNER("OWNER"),
    SUBDIVISION_OWNER("SUBDIVISION_OWNER");

    private final String id;

    AccessLevel(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static AccessLevel fromId(String id) {
        for (AccessLevel value : values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
