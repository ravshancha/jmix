package uz.kapitalbank.umida.view.reporting;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.select.JmixSelect;
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
import io.jmix.reports.entity.Report;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.enums.AccessLevel;
import uz.kapitalbank.umida.enums.RequestScope;
import uz.kapitalbank.umida.service.ReportAccessService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Диалог «Запросить доступ»: заявитель указывает причину и для кого просит доступ.
 * <p>
 * Адресат — сотрудник, выбранный на вкладке «Доступы» (владелец отчёта или обладатель уровня
 * GRANT); именно ему и владельцу предстоит решать заявку.
 */
@ViewController(id = "RequestAccessDialogView")
@ViewDescriptor(path = "request-access-dialog-view.xml")
@DialogMode(width = "50em", resizable = true)
public class RequestAccessDialogView extends StandardView {

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
    private TypedTextField<String> addresseeField;
    @ViewComponent
    private JmixTextArea reasonField;
    @ViewComponent
    private JmixSelect<RequestScope> scopeField;
    @ViewComponent
    private TypedTextField<String> levelField;

    private Report report;
    private User addressee;
    private AccessLevel requestedLevel = AccessLevel.READ;

    @Subscribe
    public void onInit(InitEvent event) {
        Map<RequestScope, String> scopes = new LinkedHashMap<>();
        scopes.put(RequestScope.SELF, messageBundle.getMessage("scope.self"));
        // Доступ на подразделение целиком пока только выбирается — выдача будет на следующем этапе.
        scopes.put(RequestScope.DEPARTMENT, messageBundle.getMessage("scope.department"));
        scopeField.setItems(scopes.keySet().toArray(new RequestScope[0]));
        scopeField.setItemLabelGenerator(scopes::get);
        scopeField.setValue(RequestScope.SELF);
    }

    public void setReport(Report report) {
        this.report = report;
        reportLabel.setText(messageBundle.formatMessage("reportSubject", report.getName()));
        User user = currentUser();
        fullNameField.setValue(ReportAccessService.displayName(user));
        usernameField.setValue(user.getUsername());
        // С действующим READ заявка означает повышение до права выдавать доступ другим.
        requestedLevel = reportAccessService.requestedLevel(user, report);
        levelField.setValue(messageBundle.getMessage(requestedLevel == AccessLevel.GRANT
                ? "requestAccessDialog.level.grant"
                : "requestAccessDialog.level.read"));
    }

    public void setAddressee(User addressee) {
        this.addressee = addressee;
        addresseeField.setValue(ReportAccessService.displayName(addressee));
    }

    @Subscribe("requestButton")
    public void onRequest(ClickEvent<Button> event) {
        String reason = reasonField.getValue();
        if (reason == null || reason.isBlank()) {
            reasonField.setInvalid(true);
            reasonField.setErrorMessage(messageBundle.getMessage("requestAccessDialog.reasonRequired"));
            return;
        }
        reasonField.setInvalid(false);
        reportAccessService.request(currentUser(), report, addressee, reason.trim(),
                scopeField.getValue(), requestedLevel);
        notifications.create(messageBundle.getMessage("requestSent")).show();
        close(StandardOutcome.SAVE);
    }

    @Subscribe("closeButton")
    public void onCloseButton(ClickEvent<Button> event) {
        close(StandardOutcome.CLOSE);
    }

    private User currentUser() {
        UserDetails user = currentAuthentication.getUser();
        return user instanceof User appUser ? appUser : null;
    }
}
