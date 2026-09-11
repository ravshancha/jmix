package uz.kapitalbank.umida.enums;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

/**
 * Отметка в списке «у кого просить доступ»: этот сотрудник уже выдал текущий доступ, либо к нему
 * ушла заявка и решения ещё нет.
 */
public enum GrantorStatus implements EnumClass<String> {

    NONE("NONE"),
    PENDING("PENDING"),
    GRANTED("GRANTED");

    private final String id;

    GrantorStatus(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static GrantorStatus fromId(String id) {
        for (GrantorStatus value : values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
