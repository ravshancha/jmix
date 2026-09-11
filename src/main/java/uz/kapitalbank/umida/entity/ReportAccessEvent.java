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
import uz.kapitalbank.umida.enums.AccessEventType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Строка вкладки «История»: что произошло с доступом к отчёту, когда, у кого и по чьему решению.
 * Пишется сервисом и только на чтение — правок и удалений у записи журнала нет, поэтому и
 * версия не нужна.
 */
@JmixEntity
@Entity
@Table(name = "REPORT_ACCESS_EVENT", indexes = {
        @Index(name = "IDX_REPORT_ACCESS_EVENT_REPORT", columnList = "REPORT_ID, EVENT_DATE"),
        @Index(name = "IDX_REPORT_ACCESS_EVENT_SUBJECT", columnList = "SUBJECT_USER_ID")
})
public class ReportAccessEvent {

    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "REPORT_ID", nullable = false)
    private Report report;

    @Column(name = "EVENT_DATE", nullable = false)
    private LocalDateTime eventDate;

    @Column(name = "EVENT_TYPE", nullable = false, length = 50)
    private String eventType;

    /**
     * Сотрудник, о доступе которого идёт речь (колонка «Пользователь» в истории).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SUBJECT_USER_ID")
    private User subjectUser;

    /**
     * Кто выполнил действие: заявитель для «Запрос создан», решающий — для остальных событий.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ACTOR_USER_ID")
    private User actorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REQUEST_ID")
    private ReportAccessRequest request;

    @Column(name = "REQUEST_REASON", length = 1000)
    private String requestReason;

    @Column(name = "REJECTION_REASON", length = 1000)
    private String rejectionReason;

    @Column(name = "VALID_FROM")
    private LocalDate validFrom;

    @Column(name = "VALID_TO")
    private LocalDate validTo;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Report getReport() {
        return report;
    }

    public void setReport(Report report) {
        this.report = report;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDateTime eventDate) {
        this.eventDate = eventDate;
    }

    public AccessEventType getEventType() {
        return eventType == null ? null : AccessEventType.fromId(eventType);
    }

    public void setEventType(AccessEventType eventType) {
        this.eventType = eventType == null ? null : eventType.getId();
    }

    public User getSubjectUser() {
        return subjectUser;
    }

    public void setSubjectUser(User subjectUser) {
        this.subjectUser = subjectUser;
    }

    public User getActorUser() {
        return actorUser;
    }

    public void setActorUser(User actorUser) {
        this.actorUser = actorUser;
    }

    public ReportAccessRequest getRequest() {
        return request;
    }

    public void setRequest(ReportAccessRequest request) {
        this.request = request;
    }

    public String getRequestReason() {
        return requestReason;
    }

    public void setRequestReason(String requestReason) {
        this.requestReason = requestReason;
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
}
