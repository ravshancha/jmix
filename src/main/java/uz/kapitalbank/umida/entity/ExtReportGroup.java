package uz.kapitalbank.umida.entity;

import io.jmix.core.entity.annotation.ReplaceEntity;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.reports.entity.ReportGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * Расширение группы отчётов аддона: оформление карточки и вложенность.
 * <p>
 * {@link ReplaceEntity} подменяет {@link ReportGroup} в метаданных, поэтому все места, которые
 * работают с группами — репозиторий аддона, редактор, списки — получают уже этот класс, а
 * дополнительные поля лежат в той же таблице {@code REPORT_GROUP}.
 * <p>
 * Группы, объявленные аннотациями в коде, остаются обычными {@code ReportGroup}: они не хранятся
 * в базе, поэтому ни родителя, ни оформления у них нет.
 */
@JmixEntity
@Entity(name = "umida_ExtReportGroup")
@ReplaceEntity(ReportGroup.class)
public class ExtReportGroup extends ReportGroup {

    private static final long serialVersionUID = 1L;

    /**
     * Имя константы {@code com.vaadin.flow.component.icon.VaadinIcon}, например {@code FOLDER}.
     */
    @Column(name = "ICON", length = 50)
    private String icon;

    /**
     * Цвет карточки в формате {@code #rrggbb}.
     */
    @Column(name = "COLOR", length = 20)
    private String color;

    /**
     * Родительская группа. {@code null} — группа верхнего уровня.
     */
    @JoinColumn(name = "PARENT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ExtReportGroup parent;

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public ExtReportGroup getParent() {
        return parent;
    }

    public void setParent(ExtReportGroup parent) {
        this.parent = parent;
    }
}
