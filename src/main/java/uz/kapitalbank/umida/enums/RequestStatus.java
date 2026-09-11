package uz.kapitalbank.umida.enums;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

/**
 * Состояние заявки на доступ: ожидает решения, доступ выдан, отказано, доступ отозван после выдачи.
 */
public enum RequestStatus implements EnumClass<String> {

    PENDING("PENDING"),
    SUCCESS("SUCCESS"),
    CANCEL("CANCEL"),
    REVOKE("REVOKE");

    private final String id;

    RequestStatus(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static RequestStatus fromId(String id) {
        for (RequestStatus value : values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
