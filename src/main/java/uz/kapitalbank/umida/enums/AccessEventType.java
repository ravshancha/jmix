package uz.kapitalbank.umida.enums;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

/**
 * Что произошло с доступом — строки вкладки «История».
 */
public enum AccessEventType implements EnumClass<String> {

    REQUEST_CREATED("REQUEST_CREATED"),
    ACCESS_GRANTED("ACCESS_GRANTED"),
    ACCESS_CHANGED("ACCESS_CHANGED"),
    ACCESS_REJECTED("ACCESS_REJECTED"),
    ACCESS_REVOKED("ACCESS_REVOKED"),
    ACCESS_EXPIRED("ACCESS_EXPIRED");

    private final String id;

    AccessEventType(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static AccessEventType fromId(String id) {
        for (AccessEventType value : values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
