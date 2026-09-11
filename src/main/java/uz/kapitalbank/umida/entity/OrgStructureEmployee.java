package uz.kapitalbank.umida.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "ORG_STRUCTURE_EMPLOYEE", indexes = {
        @Index(name = "IDX_ORG_STRUCTURE_EMPLOYEE_EMAIL", columnList = "EMAIL")
})
public class OrgStructureEmployee {

    @Id
    @Column(name = "ID")
    @JmixGeneratedValue
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @InstanceName
    @Column(name = "FULLNAME")
    private String fullname;

    @Column(name = "EMAIL", length = 100)
    private String email;

    // Avtomatik to'ldiriladi: OrgStructureEmployeeUserLinkListener saqlashdan oldin
    // email bo'yicha User'ni topib shu yerga bog'laydi (agar hali bog'lanmagan bo'lsa).
    // OneToOne, а не ManyToOne: сотрудник и пользователь соотносятся один к одному, и только
    // такая связь позволяет ходить обратно — User#getOrgEmployee(), на котором держатся колонки
    // «Должность» и «Подразделение» в журнале. Колонка в базе та же самая.
    @JoinColumn(name = "USER_ID")
    @OneToOne(fetch = FetchType.LAZY)
    private User user;

    @Column(name = "POSITION_NAME", length = 300)
    private String positionName;

    @Column(name = "IS_HEAD_OF_SUBDIVISION")
    private Integer isHead;

    @Column(name = "ORGANIZATION", length = 300)
    private String organization;

    @Column(name = "ORGANIZATION_ID", length = 36)
    private String organizationId;

    @Column(name = "DEPARTMENT", length = 300)
    private String department;

    @Column(name = "DEPARTMENT_ID", length = 36)
    private String departmentId;

    @Column(name = "DIVISION", length = 300)
    private String division;

    @Column(name = "DIVISION_ID", length = 36)
    private String divisionId;

    @Column(name = "SECTION_NAME", length = 300)
    private String sectionName;

    @Column(name = "SECTION_ID", length = 36)
    private String sectionId;

    @Column(name = "UNIT", length = 300)
    private String unit;

    @Column(name = "UNIT_ID", length = 36)
    private String unitId;

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

    public String getFullname() {
        return fullname;
    }

    public void setFullname(String fullname) {
        this.fullname = fullname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getPositionName() {
        return positionName;
    }

    public void setPositionName(String positionName) {
        this.positionName = positionName;
    }

    public Integer getIsHead() {
        return isHead;
    }

    public void setIsHead(Integer isHead) {
        this.isHead = isHead;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getDivision() {
        return division;
    }

    public void setDivision(String division) {
        this.division = division;
    }

    public String getDivisionId() {
        return divisionId;
    }

    public void setDivisionId(String divisionId) {
        this.divisionId = divisionId;
    }

    public String getSectionName() {
        return sectionName;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    public String getSectionId() {
        return sectionId;
    }

    public void setSectionId(String sectionId) {
        this.sectionId = sectionId;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getUnitId() {
        return unitId;
    }

    public void setUnitId(String unitId) {
        this.unitId = unitId;
    }
}
