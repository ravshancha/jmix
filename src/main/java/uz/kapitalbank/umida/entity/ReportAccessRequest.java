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
import uz.kapitalbank.umida.enums.AccessLevel;
import uz.kapitalbank.umida.enums.RequestScope;
import uz.kapitalbank.umida.enums.RequestStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Заявка сотрудника на доступ к отчёту.
 * <p>
 * {@link #addressee} — тот, у кого просят: владелец отчёта либо сотрудник с уровнем GRANT,
 * выбранный заявителем на вкладке «Доступы». Решение принимает адресат или владелец.
 */
@JmixEntity
@Entity
@Table(name = "REPORT_ACCESS_REQUEST", indexes = {
        @Index(name = "IDX_REPORT_ACCESS_REQUEST_REQUESTER", columnList = "REQUESTER_ID"),
        @Index(name = "IDX_REPORT_ACCESS_REQUEST_REPORT", columnList = "REPORT_ID"),
        @Index(name = "IDX_REPORT_ACCESS_REQUEST_ADDRESSEE", columnList = "ADDRESSEE_ID")
})
public class ReportAccessRequest {

    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "REQUESTER_ID", nullable = false)
    private User requester;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "REPORT_ID", nullable = false)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ADDRESSEE_ID")
    private User addressee;

    @Column(name = "STATUS", nullable = false, length = 50)
    private String status;

    @Column(name = "SCOPE_", length = 50)
    private String scope;

    /**
     * Какой уровень просят: READ по умолчанию, GRANT — когда доступ уже есть и сотрудник просит
     * право выдавать его другим.
     */
    @Column(name = "REQUESTED_LEVEL", length = 50)
    private String requestedLevel;

    @Column(name = "REASON", length = 1000)
    private String reason;

    @Column(name = "REQUESTED_AT", nullable = false)
    private LocalDateTime requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DECIDED_BY_ID")
    private User decidedBy;

    @Column(name = "DECIDED_AT")
    private LocalDateTime decidedAt;

    /**
     * Заполняется при отказе и при отзыве выданного доступа.
     */
    @Column(name = "REJECTION_REASON", length = 1000)
    private String rejectionReason;

    @Column(name = "VALID_FROM")
    private LocalDate validFrom;

    /**
     * Пустое значение — доступ выдан бессрочно.
     */
    @Column(name = "VALID_TO")
    private LocalDate validTo;

    /**
     * Галочка «Разрешить предоставление доступа» в диалоге выдачи: заявитель получает уровень
     * GRANT вместо READ.
     */
    @Column(name = "CAN_GRANT")
    private Boolean canGrant = false;

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

    public User getRequester() {
        return requester;
    }

    public void setRequester(User requester) {
        this.requester = requester;
    }

    public Report getReport() {
        return report;
    }

    public void setReport(Report report) {
        this.report = report;
    }

    public User getAddressee() {
        return addressee;
    }

    public void setAddressee(User addressee) {
        this.addressee = addressee;
    }

    public RequestStatus getStatus() {
        return status == null ? null : RequestStatus.fromId(status);
    }

    public void setStatus(RequestStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public RequestScope getScope() {
        return scope == null ? null : RequestScope.fromId(scope);
    }

    public void setScope(RequestScope scope) {
        this.scope = scope == null ? null : scope.getId();
    }

    public AccessLevel getRequestedLevel() {
        return requestedLevel == null ? null : AccessLevel.fromId(requestedLevel);
    }

    public void setRequestedLevel(AccessLevel requestedLevel) {
        this.requestedLevel = requestedLevel == null ? null : requestedLevel.getId();
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public User getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(User decidedBy) {
        this.decidedBy = decidedBy;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
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

    public Boolean getCanGrant() {
        return canGrant;
    }

    public void setCanGrant(Boolean canGrant) {
        this.canGrant = canGrant;
    }
}
