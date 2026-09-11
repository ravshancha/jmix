package uz.kapitalbank.umida.view.reporting;

import uz.kapitalbank.umida.entity.ExtReportGroup;

import java.util.List;

/**
 * Оформление карточек групп: палитра, набор иконок и значения по умолчанию.
 * <p>
 * Иконка и цвет — поля самой группы ({@link ExtReportGroup}). У групп, объявленных аннотациями
 * в коде, они пустые: такие группы в базе не лежат, поэтому цвет для них считается по коду —
 * так карточки не становятся одинаково серыми, а цвет не меняется между перезагрузками.
 */
public final class ReportGroupStyles {

    /**
     * Палитра редактора. Первый цвет — нейтральный серый.
     */
    public static final List<String> COLORS = List.of(
            "#b8bec9", "#f5c542", "#f97c4a", "#f4635a",
            "#f368a0", "#f447d0", "#b07cf0", "#6c63e8",
            "#6fb2f0", "#29b6d8", "#34d399", "#6ee787");

    /**
     * Иконки, из которых выбирают в редакторе. Имена — константы
     * {@code com.vaadin.flow.component.icon.VaadinIcon}.
     */
    public static final List<String> ICONS = List.of(
            "FOLDER", "FOLDER_OPEN", "RECORDS", "CHART", "USER_CARD", "CREDIT_CARD",
            "WALLET", "EDIT", "INSTITUTION", "MONEY", "MONEY_EXCHANGE", "TRENDING_UP",
            "BRIEFCASE", "PICTURE", "STAR_HALF_LEFT", "USERS", "OFFICE", "ACADEMY_CAP",
            "BOOK", "COMMENT", "CLIPBOARD_CHECK", "CALENDAR", "LINES_LIST", "FILE_TEXT",
            "BAR_CHART", "CHART_GRID", "GROUP", "PIE_CHART", "LOCK", "CHART_3D",
            "SHIELD", "CHECK_CIRCLE", "COPY", "DATABASE", "CONNECT", "GLOBE");

    public static final String DEFAULT_ICON = "FOLDER";

    /**
     * Иконка отчёта в списке содержимого группы.
     */
    public static final String REPORT_ICON = "FILE_TEXT";

    private ReportGroupStyles() {
    }

    public static String iconOf(ExtReportGroup group) {
        return group.getIcon() != null ? group.getIcon() : DEFAULT_ICON;
    }

    public static String colorOf(ExtReportGroup group) {
        if (group.getColor() != null) {
            return group.getColor();
        }
        String key = group.getCode() != null ? group.getCode() : String.valueOf(group.getTitle());
        return COLORS.get(Math.floorMod(key.hashCode(), COLORS.size()));
    }
}
