package uz.kapitalbank.umida.entity.orgstructure;

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
import org.springframework.lang.Nullable;

/**
 * Подразделение оргструктуры.
 * <p>
 * Справочник ведётся не в приложении, а в витрине DWH, откуда таблица наполняется выгрузкой —
 * так же, как {@code ORG_STRUCTURE_EMPLOYEE}. Поэтому экран подразделений только на чтение.
 * <p>
 * Идентификатор — строка, а не {@code UUID}: витрина хранит его как {@code varchar(36)}, и
 * менять его на своей стороне нельзя, иначе выгрузка перестанет совпадать.
 */
@JmixEntity
@Table(name = "ORG_STRUCTURE_SUBDIVISION", indexes = {
        @Index(name = "IDX_ORG_STRUCTURE_SUBDIVISION_PARENT", columnList = "PARENT_ID")
})
@Entity(name = "umida_OrgStructureSubdivision")
public class OrgStructureSubdivision {

    @Column(name = "ID", nullable = false, length = 36)
    @Id
    private String id;

    @InstanceName
    @Column(name = "NAME", length = 300)
    private String name;

    /**
     * Вышестоящее подразделение. {@code null} — верхний уровень оргструктуры.
     */
    @JoinColumn(name = "PARENT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private OrgStructureSubdivision parent;

    public OrgStructureSubdivision() {
    }

    public OrgStructureSubdivision(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    @Nullable
    public OrgStructureSubdivision getParent() {
        return parent;
    }

    public void setParent(@Nullable OrgStructureSubdivision parent) {
        this.parent = parent;
    }
}
