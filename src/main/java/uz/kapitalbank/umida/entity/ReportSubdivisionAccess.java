package uz.kapitalbank.umida.entity;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.reports.entity.Report;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Доступ к отчёту, выданный подразделению целиком: запускать отчёт могут все сотрудники этого
 * подразделения и вложенных в него. Выдаёт его владелец отчёта, владелец домена от бизнеса или
 * администратор — без заявки, со вкладки «Подразделения».
 * <p>
 * Уровень всегда READ: право выдавать доступ другим даётся только конкретному сотруднику, иначе
 * его получил бы каждый человек подразделения.
 */
@JmixEntity
@Entity
@Table(name = "REPORT_SUBDIVISION_ACCESS", indexes = {
        @Index(name = "IDX_REPORT_SUBDIVISION_ACCESS_REPORT", columnList = "REPORT_ID, SUBDIVISION_ID"),
        @Index(name = "IDX_REPORT_SUBDIVISION_ACCESS_SUBDIVISION", columnList = "SUBDIVISION_ID")
})
public class ReportSubdivisionAccess {

    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "REPORT_ID", nullable = false)
    private Report report;

    /**
     * Справочник подразделений перезаливается из витрины, поэтому исчезнувшее подразделение
     * забирает с собой и свои доступы.
     */
    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SUBDIVISION_ID", nullable = false)
    private OrgStructureSubdivision subdivision;

    @Column(name = "VALID_FROM")
    private LocalDate validFrom;

    /**
     * Пустое значение — доступ бессрочный.
     */
    @Column(name = "VALID_TO")
    private LocalDate validTo;

    /**
     * Снимается при отзыве; отозванная строка остаётся ради истории.
     */
    @Column(name = "ACTIVE", nullable = false)
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GRANTED_BY_ID")
    private User grantedBy;

    @Column(name = "GRANTED_AT")
    private LocalDateTime grantedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REVOKED_BY_ID")
    private User revokedBy;

    @Column(name = "REVOKED_AT")
    private LocalDateTime revokedAt;

    @Column(name = "REVOKE_REASON", length = 1000)
    private String revokeReason;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Report getReport() {
        return report;
    }

    public void setReport(Report report) {
        this.report = report;
    }

    public OrgStructureSubdivision getSubdivision() {
        return subdivision;
    }

    public void setSubdivision(OrgStructureSubdivision subdivision) {
        this.subdivision = subdivision;
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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public User getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(User grantedBy) {
        this.grantedBy = grantedBy;
    }

    public LocalDateTime getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(LocalDateTime grantedAt) {
        this.grantedAt = grantedAt;
    }

    public User getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(User revokedBy) {
        this.revokedBy = revokedBy;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevokeReason() {
        return revokeReason;
    }

    public void setRevokeReason(String revokeReason) {
        this.revokeReason = revokeReason;
    }
}
