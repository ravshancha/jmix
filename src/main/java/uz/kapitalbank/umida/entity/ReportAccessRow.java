package uz.kapitalbank.umida.entity;

import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Id;
import uz.kapitalbank.umida.enums.AccessLevel;
import uz.kapitalbank.umida.enums.GrantorStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Строка списков экрана «Доступы»: сотрудник и его права на отчёт. Не сохраняется — собирается в
 * памяти из {@code UserReport.owner} и {@link ReportAccess}, потому что владелец отдельной строки
 * доступа не имеет, а показывать их надо в одной таблице.
 */
@JmixEntity(name = "umida_ReportAccessRow")
public class ReportAccessRow {

    @Id
    private UUID id;

    private UUID userId;

    /**
     * Идентификатор строки доступа; пустой у владельца.
     */
    private UUID accessId;

    @InstanceName
    private String fullName;

    private String username;

    private AccessLevel accessLevel;

    private LocalDate validFrom;

    private LocalDate validTo;

    /**
     * Только для вкладки «Доступы» у заявителя: выдал ли этот сотрудник текущий доступ и не висит
     * ли на нём заявка.
     */
    private GrantorStatus grantorStatus;

    public GrantorStatus getGrantorStatus() {
        return grantorStatus;
    }

    public void setGrantorStatus(GrantorStatus grantorStatus) {
        this.grantorStatus = grantorStatus;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getAccessId() {
        return accessId;
    }

    public void setAccessId(UUID accessId) {
        this.accessId = accessId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public AccessLevel getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(AccessLevel accessLevel) {
        this.accessLevel = accessLevel;
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
