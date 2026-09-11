package uz.kapitalbank.umida.entity;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.InstanceName;
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
import org.springframework.lang.Nullable;

import uz.kapitalbank.umida.enums.ReportDomain;

import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "USER_REPORT", indexes = {
        @Index(name = "IDX_USER_REPORT_OWNER", columnList = "OWNER_ID"),
        @Index(name = "IDX_USER_REPORT_REPORT", columnList = "REPORT_ID")
})
public class UserReport {

    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "OWNER_ID", nullable = false)
    private User owner;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "REPORT_ID", nullable = false)
    private Report report;

    /**
     * Предметная область отчёта, см. {@link ReportDomain}.
     * <p>
     * Домен относится к самому отчёту, а не к владельцу строки, но хранится здесь: в «Отчётности»
     * на отчёт приходится ровно одна строка (см. {@code UserReportSyncService}), а сущность
     * {@code Report} принадлежит аддону и расширять её ради одного поля не нужно. Благодаря этому
     * фильтр и колонка списка работают обычным JPQL, без join-ов и фильтрации в памяти.
     */
    @Column(name = "DOMAIN", length = 50)
    private String domain;

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

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public Report getReport() {
        return report;
    }

    public void setReport(Report report) {
        this.report = report;
    }

    @Nullable
    public ReportDomain getDomain() {
        return domain == null ? null : ReportDomain.fromId(domain);
    }

    public void setDomain(@Nullable ReportDomain domain) {
        this.domain = domain == null ? null : domain.getId();
    }

    @InstanceName
    public String getInstanceName() {
        return report == null ? "" : report.getName();
    }
}
