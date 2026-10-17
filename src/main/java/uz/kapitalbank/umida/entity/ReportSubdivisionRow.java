package uz.kapitalbank.umida.entity;

import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Id;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Строка дерева вкладки «Подразделения»: подразделение оргструктуры и его доступ к отчёту. Не
 * сохраняется — собирается в памяти из справочника подразделений и {@link ReportSubdivisionAccess},
 * чтобы в списке были все подразделения, а не только те, кому доступ уже выдан.
 */
@JmixEntity(name = "umida_ReportSubdivisionRow")
public class ReportSubdivisionRow {

    /**
     * Идентификатор подразделения из справочника.
     */
    @Id
    private String id;

    @InstanceName
    private String name;

    /**
     * Строка вышестоящего подразделения — по ней дерево раскладывает уровни.
     */
    private ReportSubdivisionRow parent;

    /**
     * Строка собственного доступа подразделения; пусто, если доступа нет.
     */
    private UUID accessId;

    private LocalDate validFrom;

    private LocalDate validTo;

    private String grantedBy;

    private LocalDateTime grantedAt;

    /**
     * Вышестоящее подразделение, через которое доступ уже есть: своего доступа подразделению
     * тогда не нужно, его сотрудники запускают отчёт и так.
     */
    private String inheritedFrom;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Nullable
    public ReportSubdivisionRow getParent() {
        return parent;
    }

    public void setParent(@Nullable ReportSubdivisionRow parent) {
        this.parent = parent;
    }

    @Nullable
    public UUID getAccessId() {
        return accessId;
    }

    public void setAccessId(@Nullable UUID accessId) {
        this.accessId = accessId;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(String grantedBy) {
        this.grantedBy = grantedBy;
    }

    public LocalDateTime getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(LocalDateTime grantedAt) {
        this.grantedAt = grantedAt;
    }

    @Nullable
    public String getInheritedFrom() {
        return inheritedFrom;
    }

    public void setInheritedFrom(@Nullable String inheritedFrom) {
        this.inheritedFrom = inheritedFrom;
    }
}
