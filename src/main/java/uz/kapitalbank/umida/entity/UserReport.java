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
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;

import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "USER_REPORT", indexes = {
        @Index(name = "IDX_USER_REPORT_OWNER", columnList = "OWNER_ID"),
        @Index(name = "IDX_USER_REPORT_REPORT", columnList = "REPORT_ID"),
        @Index(name = "IDX_USER_REPORT_DOMAIN", columnList = "DOMAIN_ID"),
        @Index(name = "IDX_USER_REPORT_SUBDIVISION", columnList = "SUBDIVISION_ID")
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
     * {@code Report} принадлежит аддону и расширять её ради одного поля не нужно.
     * <p>
     * Удаление области, на которую ссылаются отчёты, запрещено: иначе строки списка остались бы
     * с битой ссылкой, а разрез «по домену» — без части отчётов.
     */
    @OnDeleteInverse(DeletePolicy.DENY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DOMAIN_ID")
    private ReportDomain domain;

    /**
     * Подразделение-владелец отчёта: в «Отчётности» именно оно стоит в колонке «Владелец», а
     * создавший отчёт сотрудник остаётся на экране «Доступы».
     * <p>
     * Удаление подразделения, на которое ссылаются отчёты, запрещено — как и для области: иначе
     * строки списка остались бы без владельца.
     */
    @OnDeleteInverse(DeletePolicy.DENY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SUBDIVISION_ID")
    private OrgStructureSubdivision subdivision;

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
        return domain;
    }

    public void setDomain(@Nullable ReportDomain domain) {
        this.domain = domain;
    }

    @Nullable
    public OrgStructureSubdivision getSubdivision() {
        return subdivision;
    }

    public void setSubdivision(@Nullable OrgStructureSubdivision subdivision) {
        this.subdivision = subdivision;
    }

    @InstanceName
    public String getInstanceName() {
        return report == null ? "" : report.getName();
    }
}
