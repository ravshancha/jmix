package uz.kapitalbank.umida.security;

import io.jmix.reports.entity.Report;
import io.jmix.reports.entity.ReportExecution;
import io.jmix.reports.entity.ReportGroup;
import io.jmix.reports.entity.ReportTemplate;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.model.SecurityScope;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.security.role.annotation.SpecificPolicy;
import io.jmix.securityflowui.role.UiMinimalPolicies;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;
import uz.kapitalbank.umida.entity.ReportAccess;
import uz.kapitalbank.umida.entity.ReportAccessEvent;
import uz.kapitalbank.umida.entity.ReportAccessRequest;
import uz.kapitalbank.umida.entity.ReportAccessRow;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.UserReport;


/**
 * Базовая роль отчётности: вход в приложение, просмотр списка отчётов, истории исполнения и
 * запуск отчёта. Ведение самих отчётов даёт {@link ReportingUserOwnerRole}.
 * <p>
 * Роль самодостаточна: вход и главный экран входят в неё, отдельно назначать {@code ui-minimal}
 * не нужно.
 */
@ResourceRole(name = "Reporting: basic", code = ReportingUserBasicRole.CODE, scope = SecurityScope.UI)
public interface ReportingUserBasicRole extends UiMinimalPolicies {

    String CODE = "reporting-basic-user";

    /**
     * Вход в приложение и главный экран — без них пользователь с одной этой ролью не может
     * залогиниться.
     */
    @ViewPolicy(viewIds = {"umida_LoginView", "umida_MainView"})
    @SpecificPolicy(resources = {"ui.loginToUi", "ui.showExceptionDetails"})
    void login();

    @ViewPolicy(viewIds = {
            "umida_ReportingMainView",
            "umida_ReportingAnalyticsView",
            "umida_ReportingGroupsView",
            "ReportExecution.history",
            "Report.access",
            "RequestAccessDialogView",
            "GrantAccessDialogView"
    })
    // Пункты лежат внутри группы «Отчетность», а группа скрыта, пока на неё нет прав.
    // Группы отчётов сотрудник видит как каталог: ведение групп там скрыто, остаётся навигация
    // по ним и запуск отчётов из карточек.
    @MenuPolicy(menuIds = {"reporting", "umida_ReportingMainView", "umida_ReportingAnalyticsView",
            "umida_ReportingGroupsView"})
    void reporting();

    /**
     * Список отчётов и колонки «Отчёт» / «Группа» / «Владелец».
     */
    @EntityPolicy(entityClass = UserReport.class, actions = EntityPolicyAction.READ)
    @EntityPolicy(entityClass = Report.class, actions = EntityPolicyAction.READ)
    @EntityPolicy(entityClass = ReportGroup.class, actions = EntityPolicyAction.READ)
    @EntityPolicy(entityClass = User.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = UserReport.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityAttributePolicy(entityClass = Report.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityAttributePolicy(entityClass = ReportGroup.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityAttributePolicy(entityClass = User.class,
            attributes = {"username", "firstName", "lastName", "email"},
            action = EntityAttributePolicyAction.VIEW)
    void reportList();

    /**
     * Модель доступа только на чтение: заявки, выданные доступы и журнал показываются на экране
     * «Доступы», а пишет их сервис под системным пользователем — у сотрудника прав на чужие
     * строки нет.
     */
    @EntityPolicy(entityClass = ReportAccess.class, actions = EntityPolicyAction.READ)
    @EntityPolicy(entityClass = ReportAccessRequest.class, actions = EntityPolicyAction.READ)
    @EntityPolicy(entityClass = ReportAccessEvent.class, actions = EntityPolicyAction.READ)
    @EntityPolicy(entityClass = ReportAccessRow.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = ReportAccess.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityAttributePolicy(entityClass = ReportAccessRequest.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityAttributePolicy(entityClass = ReportAccessEvent.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityAttributePolicy(entityClass = ReportAccessRow.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void reportAccess();

    /**
     * Всё, что нужно для запуска отчёта (повторяет встроенную роль {@code report-run}): чтение
     * шаблонов, диалог параметров и запись запуска в историю исполнения.
     */
    @ViewPolicy(viewIds = {
            "report_InputParametersDialogView",
            "report_ReportRunView",
            "report_ReportTableView"
    })
    @EntityPolicy(entityClass = ReportTemplate.class, actions = EntityPolicyAction.READ)
    @EntityPolicy(entityClass = ReportExecution.class,
            actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = ReportTemplate.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityAttributePolicy(entityClass = ReportExecution.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    void reportRun();
}
