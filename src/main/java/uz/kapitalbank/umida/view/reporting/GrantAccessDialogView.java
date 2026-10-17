package uz.kapitalbank.umida.view.reporting;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.MessageBundle;
import io.jmix.flowui.view.StandardOutcome;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import io.jmix.reports.entity.Report;
import org.springframework.lang.Nullable;
import uz.kapitalbank.umida.entity.ReportAccess;
import uz.kapitalbank.umida.entity.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.ReportAccessRequest;
import uz.kapitalbank.umida.entity.ReportSubdivisionAccess;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.enums.AccessLevel;
import uz.kapitalbank.umida.service.ReportAccessService;

import java.time.LocalDate;

/**
 * Диалог «Предоставление доступа». Открывается в трёх случаях: владелец (или обладатель GRANT)
 * подтверждает заявку; распорядитель доступов выдаёт или настраивает доступ сотруднику без заявки
 * — вкладка «Все»; распорядитель настраивает срок доступа подразделения — вкладка
 * «Подразделения». В последнем случае галочки нет: подразделению выдаётся только запуск.
 * <p>
 * Дата начала подставляется сегодняшняя, дата окончания по умолчанию пустая — это бессрочный
 * доступ. Галочка «Разрешить предоставление доступа» выдаёт уровень GRANT — сотрудник сможет
 * выдавать доступ к этому отчёту другим; по заявке на повышение она приходит уже отмеченной.
 */
@ViewController(id = "GrantAccessDialogView")
@ViewDescriptor(path = "grant-access-dialog-view.xml")
@DialogMode(width = "50em", resizable = true)
public class GrantAccessDialogView extends StandardView {

    @Autowired
    private ReportAccessService reportAccessService;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private Notifications notifications;
    @Autowired
    private MessageBundle messageBundle;

    @ViewComponent
    private com.vaadin.flow.component.html.Span reportLabel;
    @ViewComponent
    private TypedTextField<String> fullNameField;
    @ViewComponent
    private TypedTextField<String> usernameField;
    @ViewComponent
    private JmixTextArea reasonField;
    @ViewComponent
    private TypedDatePicker<LocalDate> validFromField;
    @ViewComponent
    private TypedDatePicker<LocalDate> validToField;
    @ViewComponent
    private JmixCheckbox canGrantField;

    /**
     * Что сделать по кнопке «Дать доступ» — зависит от того, каким сеттером диалог заполнен.
     */
    private interface GrantAction {
        void grant(LocalDate validFrom, @Nullable LocalDate validTo, boolean canGrant);
    }

    private GrantAction action;

    public void setRequest(ReportAccessRequest request) {
        action = (validFrom, validTo, canGrant) ->
                reportAccessService.grant(request, currentUser(), validFrom, validTo, canGrant);
        reportLabel.setText(messageBundle.formatMessage("reportSubject", request.getReport().getName()));
        fullNameField.setValue(ReportAccessService.displayName(request.getRequester()));
        usernameField.setValue(request.getRequester().getUsername());
        reasonField.setValue(request.getReason() == null ? "" : request.getReason());
        validFromField.setValue(LocalDate.now());
        // Заявка на повышение приходит с уровнем GRANT — галочка сразу отмечена, но решение
        // остаётся за тем, кто выдаёт.
        canGrantField.setValue(request.getRequestedLevel() == AccessLevel.GRANT);
        canGrantField.setEnabled(true);
    }

    /**
     * Выдача без заявки. При настройке существующего доступа поля заполняются его сроком и
     * уровнем.
     */
    public void setUser(Report report, User user, @Nullable ReportAccess current) {
        action = (validFrom, validTo, canGrant) ->
                reportAccessService.grantDirect(report, user, currentUser(), validFrom, validTo, canGrant);
        reportLabel.setText(messageBundle.formatMessage("reportSubject", report.getName()));
        fullNameField.setValue(ReportAccessService.displayName(user));
        usernameField.setValue(user.getUsername());
        reasonField.setVisible(false);
        validFromField.setValue(current == null || current.getValidFrom() == null
                ? LocalDate.now() : current.getValidFrom());
        validToField.setValue(current == null ? null : current.getValidTo());
        canGrantField.setValue(current != null && current.getAccessLevel() == AccessLevel.GRANT);
    }

    /**
     * Срок доступа подразделения: вместо ФИО — название подразделения, логина, причины и галочки нет.
     */
    public void setSubdivision(Report report, OrgStructureSubdivision subdivision,
                               @Nullable ReportSubdivisionAccess current) {
        action = (validFrom, validTo, canGrant) ->
                reportAccessService.grantSubdivision(report, subdivision, currentUser(), validFrom, validTo);
        reportLabel.setText(messageBundle.formatMessage("reportSubject", report.getName()));
        fullNameField.setLabel(messageBundle.getMessage("grantAccessDialog.subdivision"));
        fullNameField.setValue(subdivision.getName() == null ? subdivision.getId() : subdivision.getName());
        usernameField.setVisible(false);
        reasonField.setVisible(false);
        canGrantField.setVisible(false);
        validFromField.setValue(current == null || current.getValidFrom() == null
                ? LocalDate.now() : current.getValidFrom());
        validToField.setValue(current == null ? null : current.getValidTo());
    }

    @Subscribe("grantButton")
    public void onGrant(ClickEvent<Button> event) {
        LocalDate validFrom = validFromField.getValue() == null ? LocalDate.now() : validFromField.getValue();
        LocalDate validTo = validToField.getValue();
        if (validTo != null && validTo.isBefore(validFrom)) {
            notifications.create(messageBundle.getMessage("grantAccessDialog.wrongPeriod"))
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }
        action.grant(validFrom, validTo, Boolean.TRUE.equals(canGrantField.getValue()));
        notifications.create(messageBundle.getMessage("accessGranted")).show();
        close(StandardOutcome.SAVE);
    }

    @Subscribe("cancelButton")
    public void onCancel(ClickEvent<Button> event) {
        close(StandardOutcome.CLOSE);
    }

    private User currentUser() {
        UserDetails user = currentAuthentication.getUser();
        return user instanceof User appUser ? appUser : null;
    }
}
