package uz.kapitalbank.umida.entity;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
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

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * Предметная область отчёта — деловой разрез списка «Отчётность» рядом с группой отчёта.
 * <p>
 * Раньше это было перечисление в коде, теперь справочник: у области появились владелец от
 * бизнеса, язык и вложенность (поддомены), а такой состав пользователи ведут сами, без правки
 * кода и файлов сообщений.
 * <p>
 * Вложенность — ссылка на себя через {@link #getParent()}: домен верхнего уровня родителя не
 * имеет, поддомен ссылается на свой домен. Глубина не ограничена, но список рассчитан на два
 * уровня — домен и поддомен.
 */
@JmixEntity
@Entity(name = "umida_ReportDomain")
@Table(name = "REPORT_DOMAIN", indexes = {
        @Index(name = "IDX_REPORT_DOMAIN_CODE", columnList = "CODE", unique = true),
        @Index(name = "IDX_REPORT_DOMAIN_PARENT", columnList = "PARENT_ID"),
        @Index(name = "IDX_REPORT_DOMAIN_BUSINESS_OWNER", columnList = "BUSINESS_OWNER_ID")
})
public class ReportDomain {

    @Id
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    /**
     * Домен, внутри которого лежит эта область. Пусто — область верхнего уровня.
     * <p>
     * Удаление домена с поддоменами запрещено: иначе поддомены остались бы без родителя, а
     * отчёты — в области, которой на верхнем уровне нет.
     */
    @OnDeleteInverse(DeletePolicy.DENY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PARENT_ID")
    private ReportDomain parent;

    /**
     * Стабильный идентификатор области для интеграций и миграций: именно по нему строки
     * справочника сопоставлены со значениями прежнего перечисления (RETAIL, CORPORATE, ...).
     */
    @Column(name = "CODE", nullable = false, length = 50)
    private String code;

    /**
     * Короткое название — то, что видно в фильтре и в колонке списка.
     */
    @Column(name = "SHORT_NAME", length = 150)
    private String shortName;

    /**
     * Полное название для отчётных форм и выгрузок.
     */
    @Column(name = "LONG_NAME", length = 500)
    private String longName;

    /**
     * Владелец области от бизнеса — сотрудник оргструктуры, а не пользователь системы: за
     * область отвечает должность в структуре, у такого человека может вообще не быть входа в
     * приложение.
     */
    @OnDeleteInverse(DeletePolicy.UNLINK)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BUSINESS_OWNER_ID")
    private OrgStructureEmployee businessOwner;

    /**
     * Язык названий области, код вида {@code uz}, {@code ru}, {@code en}.
     */
    @Column(name = "LANGUAGE", length = 10)
    private String language;

    @CreatedDate
    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;

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

    @Nullable
    public ReportDomain getParent() {
        return parent;
    }

    public void setParent(@Nullable ReportDomain parent) {
        this.parent = parent;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Nullable
    public String getShortName() {
        return shortName;
    }

    public void setShortName(@Nullable String shortName) {
        this.shortName = shortName;
    }

    @Nullable
    public String getLongName() {
        return longName;
    }

    public void setLongName(@Nullable String longName) {
        this.longName = longName;
    }

    @Nullable
    public OrgStructureEmployee getBusinessOwner() {
        return businessOwner;
    }

    public void setBusinessOwner(@Nullable OrgStructureEmployee businessOwner) {
        this.businessOwner = businessOwner;
    }

    @Nullable
    public String getLanguage() {
        return language;
    }

    public void setLanguage(@Nullable String language) {
        this.language = language;
    }

    @Nullable
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(@Nullable OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Nullable
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(@Nullable OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Название для фильтров, колонок и выпадающих списков. У строки, заведённой без короткого
     * названия, показывается код — пустая строка в списке не помогает выбрать область.
     */
    @InstanceName
    @DependsOnProperties({"shortName", "code"})
    public String getInstanceName() {
        return shortName != null && !shortName.isBlank() ? shortName : String.valueOf(code);
    }
}
