package uz.kapitalbank.umida.enums;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

/**
 * Предметная область отчёта — второй разрез списка «Отчётность» рядом с группой отчёта.
 * <p>
 * Группа отчёта ({@code report_ReportGroup}) остаётся техническим справочником аддона, а домен
 * отвечает на вопрос «чей это отчёт по бизнесу». Список значений фиксированный: он меняется вместе
 * с бизнес-структурой, а не пользователями, поэтому это перечисление, а не справочник. Идентификатор
 * хранится строкой, так что добавление значения — правка этого файла и трёх файлов сообщений,
 * миграция базы при этом не нужна.
 */
public enum ReportDomain implements EnumClass<String> {

    RETAIL("RETAIL"),
    CORPORATE("CORPORATE"),
    RISK("RISK"),
    FINANCE("FINANCE"),
    OPERATIONS("OPERATIONS"),
    HR("HR"),
    IT("IT"),
    OTHER("OTHER");

    private final String id;

    ReportDomain(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static ReportDomain fromId(String id) {
        for (ReportDomain value : values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
