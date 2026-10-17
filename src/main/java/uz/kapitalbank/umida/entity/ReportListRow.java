package uz.kapitalbank.umida.entity;

import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Id;
import org.springframework.lang.Nullable;

/**
 * Строка сгруппированного списка «Отчётности»: либо заголовок группы (группа отчётов или домен),
 * либо отчёт внутри неё. Не сохраняется — собирается в памяти из загруженной страницы
 * {@link UserReport}, чтобы дерево показывало отчёты под раскрывающимися заголовками.
 */
@JmixEntity(name = "umida_ReportListRow")
public class ReportListRow {

    @Id
    private String id;

    /**
     * Название группы у заголовка или название отчёта у строки отчёта.
     */
    @InstanceName
    private String name;

    /**
     * Заголовок группы, под которым лежит отчёт; у заголовка пусто — по нему дерево раскладывает
     * уровни.
     */
    private ReportListRow parent;

    /**
     * Строка «Отчётности», которую представляет строка дерева; у заголовка группы пусто.
     */
    private UserReport userReport;

    private String code;

    private String owner;

    private String reportGroup;

    private String domain;

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
    public ReportListRow getParent() {
        return parent;
    }

    public void setParent(@Nullable ReportListRow parent) {
        this.parent = parent;
    }

    @Nullable
    public UserReport getUserReport() {
        return userReport;
    }

    public void setUserReport(@Nullable UserReport userReport) {
        this.userReport = userReport;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getReportGroup() {
        return reportGroup;
    }

    public void setReportGroup(String reportGroup) {
        this.reportGroup = reportGroup;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }
}
