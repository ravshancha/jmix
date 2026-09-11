package uz.kapitalbank.umida.enums;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

/**
 * Для кого запрашивается доступ. DEPARTMENT пока только выбирается в диалоге — выдача доступа
 * на подразделение целиком делается на следующем этапе.
 */
public enum RequestScope implements EnumClass<String> {

    SELF("SELF"),
    DEPARTMENT("DEPARTMENT");

    private final String id;

    RequestScope(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static RequestScope fromId(String id) {
        for (RequestScope value : values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
