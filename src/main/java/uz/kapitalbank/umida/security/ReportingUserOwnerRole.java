package uz.kapitalbank.umida.security;

import io.jmix.core.entity.KeyValueEntity;
import io.jmix.reports.entity.wizard.QueryParameter;
import io.jmix.reportsflowui.role.ReportsFullAccessRole;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.model.SecurityScope;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.security.role.annotation.SpecificPolicy;
import io.jmix.securityflowui.role.UiFilterRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;
import uz.kapitalbank.umida.entity.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.ReportDomain;
import uz.kapitalbank.umida.view.reporting.ReportingMainView;

/**
 * Роль владельца отчётов: всё, что умеет {@link ReportingUserBasicRole}, плюс полный доступ к
 * модулю отчётов ({@link ReportsFullAccessRole}) — своё меню «Отчёты» аддона, редактор, мастер,
 * импорт/экспорт — и фильтры на списках ({@link UiFilterRole}).
 * <p>
 * Состав перенесён из роли {@code ldap-user} проекта toshtaxi. Роль самодостаточна и назначается
 * вручную: и собственные экраны отчётности, и меню аддона видны только тем, кому её выдали.
 * Basic-роль в дополнение выдавать не нужно — она наследуется.
 */
@ResourceRole(name = "Reporting: owner", code = ReportingUserOwnerRole.CODE, scope = SecurityScope.UI)
public interface ReportingUserOwnerRole extends ReportingUserBasicRole, ReportsFullAccessRole, UiFilterRole {

    String CODE = "reporting-owner-user";

    /**
     * Признак «владельца» для {@code Reporting.list}: по нему экран показывает кнопки создания,
     * редактирования и импорта отчёта вместо пользовательского режима.
     */
    @SpecificPolicy(resources = ReportingMainView.MANAGE_USER_REPORTS)
    void reportManage();

    /**
     * Справочник подразделений: отдельным пунктом меню рядом с областями и диалогом из поля
     * «Владелец» в карточке отчёта. Экран только на чтение — таблица наполняется выгрузкой из
     * витрины, поэтому права на запись здесь не нужны, а саму сущность на чтение выдаёт
     * {@link ReportingUserBasicRole}.
     */
    @ViewPolicy(viewIds = "umida_OrgStructureSubdivision.list")
    @MenuPolicy(menuIds = "umida_OrgStructureSubdivision.list")
    void reportSubdivisions();

    /**
     * Редактор группы ({@code umida_ReportGroupDetailView}) — он расширяет редактор аддона
     * иконкой и цветом. Сам экран групп выдаёт {@link ReportingUserBasicRole}: сотрудник тоже
     * ходит по каталогу, но ведение групп доступно только владельцу.
     */
    @ViewPolicy(viewIds = "umida_ReportGroupDetailView")
    void reportGroups();

    /**
     * Группа меню «Отчёты» самого аддона. {@link ReportsFullAccessRole} выдаёт её пункты, но не
     * саму группу — без этой строки меню остаётся скрытым.
     */
    @MenuPolicy(menuIds = "reports")
    void reportsAddonMenu();

    /**
     * Служебные экраны, без которых редактор и мастер отчётов работают не полностью:
     * редактор параметра запроса, диалоги ввода и заглушка грида при нехватке прав.
     */
    @ViewPolicy(viewIds = {
            "report_QueryParameter.detail",
            "inputDialog",
            "multiValueSelectDialog",
            "DataGridEmptyStateByPermissionsFragment"
    })
    void reportEditorViews();

    /**
     * Результаты произвольных запросов мастера отчётов приходят как {@link KeyValueEntity} —
     * без прав на неё предпросмотр в мастере пуст.
     */
    @EntityPolicy(entityClass = KeyValueEntity.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = KeyValueEntity.class, attributes = "*",
            action = EntityAttributePolicyAction.VIEW)
    void keyValueEntity();

    /**
     * Параметры JPQL-запроса в мастере отчётов ({@link QueryParameter}). В {@link ReportsFullAccessRole}
     * этой сущности нет, поэтому без политики таблица параметров на шаге «Ввод JPQL-запроса»
     * показывает «Access denied».
     */
    @EntityPolicy(entityClass = QueryParameter.class, actions = EntityPolicyAction.ALL)
    @EntityAttributePolicy(entityClass = QueryParameter.class, attributes = "*",
            action = EntityAttributePolicyAction.MODIFY)
    void queryParameter();

    /**
     * Справочник оргструктуры: владелец ведёт его сам — экран «Доступы» берёт оттуда должность и
     * подразделение сотрудника.
     */
    @ViewPolicy(viewIds = {"OrgStructureEmployee.list", "OrgStructureEmployee.detail"})
    @MenuPolicy(menuIds = {"orgstructure", "OrgStructureEmployee.list"})
    @EntityPolicy(entityClass = OrgStructureEmployee.class, actions = EntityPolicyAction.ALL)
    @EntityAttributePolicy(entityClass = OrgStructureEmployee.class, attributes = "*",
            action = EntityAttributePolicyAction.MODIFY)
    void orgStructureEmployee();

    /**
     * Справочник предметных областей ведёт владелец отчётов: домен он же и проставляет отчёту в
     * его редакторе.
     */
    @ViewPolicy(viewIds = {"umida_ReportDomain.list", "umida_ReportDomain.detail"})
    @MenuPolicy(menuIds = {"umida_ReportDomain.list"})
    @EntityPolicy(entityClass = ReportDomain.class, actions = EntityPolicyAction.ALL)
    @EntityAttributePolicy(entityClass = ReportDomain.class, attributes = "*",
            action = EntityAttributePolicyAction.MODIFY)
    void reportDomain();
}
