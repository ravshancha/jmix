package uz.kapitalbank.umida.view.reporting;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
import io.jmix.flowui.app.inputdialog.InputParameter;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.tabsheet.JmixTabSheet;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.MessageBundle;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.Supply;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.jmix.reports.entity.Report;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.security.core.userdetails.UserDetails;
import uz.kapitalbank.umida.entity.ReportAccess;
import uz.kapitalbank.umida.entity.ReportAccessEvent;
import uz.kapitalbank.umida.entity.ReportAccessRequest;
import uz.kapitalbank.umida.entity.ReportAccessRow;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.service.ReportAccessService;
import uz.kapitalbank.umida.view.main.MainView;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Экран «Доступы» по отчёту. Открывается из списка отчётов — кнопкой или пунктом контекстного
 * меню строки — и показывает разный набор вкладок в зависимости от прав.
 * <p>
 * Обычный сотрудник видит «Доступы» (у кого можно попросить) и «Историю» по себе. Владелец отчёта
 * и обладатель уровня GRANT дополнительно получают «Все» (кто и что имеет), «Подразделения»
 * (заглушка следующего этапа) и «Запросы» со счётчиком нерешённых заявок.
 */
@Route(value = "report-access/:reportId", layout = MainView.class)
@ViewController(id = "Report.access")
@ViewDescriptor(path = "report-access-view.xml")
public class ReportAccessView extends StandardView implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Autowired
    private ReportAccessService reportAccessService;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private Notifications notifications;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private DialogWindows dialogWindows;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private MessageBundle messageBundle;

    @ViewComponent
    private JmixTabSheet tabSheet;
    @ViewComponent
    private HorizontalLayout headerPanel;
    @ViewComponent
    private Span reportTitle;
    @ViewComponent
    private DataGrid<ReportAccessRow> allDataGrid;
    @ViewComponent
    private DataGrid<ReportAccessRow> grantorsDataGrid;
    @ViewComponent
    private DataGrid<ReportAccessRequest> requestsDataGrid;
    @ViewComponent
    private DataGrid<ReportAccessEvent> historyDataGrid;
    @ViewComponent
    private CollectionContainer<ReportAccessRow> allRowsDc;
    @ViewComponent
    private CollectionContainer<ReportAccessRow> grantorRowsDc;
    @ViewComponent
    private CollectionContainer<ReportAccessRequest> requestsDc;
    @ViewComponent
    private CollectionContainer<ReportAccessEvent> historyDc;
    @ViewComponent
    private JmixButton requestAccessButton;
    @ViewComponent
    private JmixButton revokeAccessButton;

    private MainView headerHost;
    private Report report;
    private boolean decisionMaker;
    /**
     * Владелец отчёта: только он закрывает и отзывает выданные доступы.
     */
    private boolean owner;

    @Subscribe
    public void onInit(InitEvent event) {
        // Кнопка «Запросить доступ» становится акцентной только когда выбран адресат — как в дизайне.
        grantorsDataGrid.addSelectionListener(selection -> updateRequestButton());

        // «‹ Доступы» и название отчёта живут в верхней панели приложения, а не в теле экрана.
        addAttachListener(e -> {
            headerHost = MainView.findMainView(this);
            if (headerHost != null) {
                headerHost.setHeaderActions(headerPanel);
            }
        });
        addDetachListener(e -> {
            if (headerHost != null) {
                headerHost.clearHeaderActions();
                headerHost = null;
            }
        });
    }

    /**
     * Отчёт передаётся параметром маршрута, чтобы экран открывался ссылкой и переживал обновление
     * страницы.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String reportId = event.getRouteParameters().get("reportId").orElse(null);
        if (reportId == null) {
            viewNavigators.view(this, ReportingMainView.class).navigate();
            return;
        }
        report = dataManager.load(Report.class).id(UUID.fromString(reportId)).one();
        reportTitle.setText(messageBundle.formatMessage("reportSubject", report.getName()));
        decisionMaker = reportAccessService.canDecide(currentUser(), report);
        owner = reportAccessService.isOwner(currentUser(), report);
        applyTabs();
        reloadAll();
    }

    /**
     * Вкладки владельца скрыты у обычного сотрудника: он не должен видеть ни чужие доступы, ни
     * чужие заявки.
     */
    private void applyTabs() {
        tabSheet.getTabAt(0).setVisible(decisionMaker);   // Все
        tabSheet.getTabAt(1).setVisible(decisionMaker);   // Подразделения
        tabSheet.getTabAt(3).setVisible(decisionMaker);   // Запросы
        tabSheet.setSelectedIndex(decisionMaker ? 0 : 2);
        // Смена индекса выше не всегда даёт событие (индекс мог и не измениться), поэтому
        // название отчёта ставится на место явно.
        placeReportTitle();
        revokeAccessButton.setVisible(owner);
    }

    /**
     * Название отчёта — общая для всех вкладок строка, но по макету она стоит под панелью вкладок,
     * то есть внутри их содержимого. Компонент один, поэтому он переезжает первым элементом в
     * содержимое активной вкладки: {@code add} в Vaadin сам убирает его с прежнего места.
     */
    @Subscribe("tabSheet")
    public void onTabSheetSelectedChange(JmixTabSheet.SelectedChangeEvent event) {
        placeReportTitle();
    }

    private void placeReportTitle() {
        Tab selectedTab = tabSheet.getSelectedTab();
        if (selectedTab == null) {
            return;
        }
        Component content = tabSheet.getContentByTab(selectedTab);
        if (content instanceof HasComponents ordered) {
            ordered.addComponentAsFirst(reportTitle);
        }
    }

    private void reloadAll() {
        if (decisionMaker) {
            allRowsDc.setItems(reportAccessService.accessRows(report));
            requestsDc.setItems(reportAccessService.pendingRequests(report));
            historyDc.setItems(reportAccessService.history(report, null));
            updateRequestsBadge();
        } else {
            historyDc.setItems(reportAccessService.history(report, currentUser()));
        }
        grantorRowsDc.setItems(reportAccessService.grantorRows(report, currentUser()));
        updateRequestButton();
    }

    /**
     * Кнопка «Запросить доступ» смотрит на выбранного сотрудника: тому, кому заявка уже
     * отправлена, второй раз не пошлёшь, а с уровнем GRANT (и владельцу) просить нечего.
     */
    private void updateRequestButton() {
        ReportAccessRow row = grantorsDataGrid.getSingleSelectedItem();
        boolean enabled = row != null && reportAccessService.canRequestFrom(currentUser(), report, row);
        requestAccessButton.setEnabled(enabled);
        requestAccessButton.setClassName("report-primary-action", enabled);
        requestAccessButton.setTitle(row != null && !enabled
                ? messageBundle.getMessage("requestBlockedHint")
                : null);
    }

    /**
     * Счётчик нерешённых заявок в подписи вкладки — «Запросы 3» на макете.
     */
    private void updateRequestsBadge() {
        int pending = requestsDc.getItems().size();
        String label = messageBundle.getMessage("tab.requests");
        tabSheet.getTabAt(3).setLabel(pending == 0 ? label : label + " " + pending);
    }

    // ------------------------------------------------------------- сотрудник

    @Subscribe("requestAccessButton")
    public void onRequestAccess(ClickEvent<Button> event) {
        ReportAccessRow row = grantorsDataGrid.getSingleSelectedItem();
        if (row == null) {
            return;
        }
        if (!reportAccessService.canRequestFrom(currentUser(), report, row)) {
            notifications.create(messageBundle.getMessage("requestAlreadyPending"))
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }
        var dialog = dialogWindows.view(this, RequestAccessDialogView.class).build();
        dialog.getView().setReport(report);
        dialog.getView().setAddressee(reportAccessService.user(row.getUserId()));
        dialog.addAfterCloseListener(e -> reloadAll());
        dialog.open();
    }

    // ------------------------------------------------------------- владелец

    @Subscribe("approveButton")
    public void onApprove(ClickEvent<Button> event) {
        ReportAccessRequest request = requestsDataGrid.getSingleSelectedItem();
        if (request == null) {
            showSelectWarning();
            return;
        }
        var dialog = dialogWindows.view(this, GrantAccessDialogView.class).build();
        dialog.getView().setRequest(request);
        dialog.addAfterCloseListener(e -> reloadAll());
        dialog.open();
    }

    @Subscribe("rejectButton")
    public void onReject(ClickEvent<Button> event) {
        ReportAccessRequest request = requestsDataGrid.getSingleSelectedItem();
        if (request == null) {
            showSelectWarning();
            return;
        }
        askReason("rejectDialogHeader", "rejectReasonRequired",
                reason -> {
                    reportAccessService.reject(request, currentUser(), reason);
                    notifications.create(messageBundle.getMessage("requestRejected")).show();
                    reloadAll();
                });
    }

    /**
     * Закрыть выданный доступ вправе только владелец отчёта: делегат с уровнем GRANT доступ
     * выдаёт, но не отзывает.
     */
    @Subscribe("revokeAccessButton")
    public void onRevokeAccess(ClickEvent<Button> event) {
        if (!owner) {
            notifications.create(messageBundle.getMessage("revokeOwnerOnly"))
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }
        ReportAccessRow row = allDataGrid.getSingleSelectedItem();
        if (row == null || row.getAccessId() == null) {
            notifications.create(messageBundle.getMessage("selectAccessRow"))
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }
        askReason("revokeDialogHeader", "revokeReasonRequired",
                reason -> {
                    ReportAccess access = dataManager.load(ReportAccess.class).id(row.getAccessId()).one();
                    reportAccessService.revoke(access, currentUser(), reason);
                    notifications.create(messageBundle.getMessage("accessRevoked")).show();
                    reloadAll();
                });
    }

    /**
     * «Настроить доступ» переиспользует диалог выдачи: там же меняются сроки и уровень.
     */
    @Subscribe("editAccessButton")
    public void onEditAccess(ClickEvent<Button> event) {
        notifications.create(messageBundle.getMessage("editAccessNotImplemented"))
                .withType(Notifications.Type.WARNING)
                .show();
    }

    @Subscribe("grantAccessButton")
    public void onGrantAccess(ClickEvent<Button> event) {
        notifications.create(messageBundle.getMessage("grantAccessFromRequests"))
                .withType(Notifications.Type.WARNING)
                .show();
    }

    @Subscribe("backButton")
    public void onBack(ClickEvent<Button> event) {
        viewNavigators.view(this, ReportingMainView.class).navigate();
    }

    // ------------------------------------------------------------- колонки

    @Supply(to = "requestsDataGrid.requesterName", subject = "renderer")
    private com.vaadin.flow.data.renderer.Renderer<ReportAccessRequest> requesterNameRenderer() {
        return new com.vaadin.flow.data.renderer.TextRenderer<>(
                request -> ReportAccessService.displayName(request.getRequester()));
    }

    @Supply(to = "requestsDataGrid.addresseeName", subject = "renderer")
    private com.vaadin.flow.data.renderer.Renderer<ReportAccessRequest> addresseeNameRenderer() {
        return new com.vaadin.flow.data.renderer.TextRenderer<>(
                request -> request.getAddressee() == null
                        ? "" : ReportAccessService.displayName(request.getAddressee()));
    }

    /**
     * Причина отказа: сотрудник вписывает её руками, а автоматическое закрытие параллельных
     * заявок кладёт в базу ключ сообщения — он и переводится здесь на язык пользователя.
     */
    @Supply(to = "historyDataGrid.rejectionReason", subject = "renderer")
    private com.vaadin.flow.data.renderer.Renderer<ReportAccessEvent> rejectionReasonRenderer() {
        return new com.vaadin.flow.data.renderer.TextRenderer<>(
                event -> localizedReason(event.getRejectionReason()));
    }

    private String localizedReason(@Nullable String reason) {
        if (reason == null) {
            return "";
        }
        return reason.startsWith("msg://")
                ? messageBundle.getMessage(reason.substring("msg://".length()))
                : reason;
    }

    /**
     * «Срок доступа» одной строкой: 24.07.2026-24.12.2026 либо «Бессрочно», если конца нет.
     */
    @Supply(to = "historyDataGrid.period", subject = "renderer")
    private com.vaadin.flow.data.renderer.Renderer<ReportAccessEvent> periodRenderer() {
        return new com.vaadin.flow.data.renderer.TextRenderer<>(event -> {
            if (event.getValidFrom() == null && event.getValidTo() == null) {
                return "";
            }
            String from = event.getValidFrom() == null ? "" : event.getValidFrom().format(DATE);
            return event.getValidTo() == null
                    ? messageBundle.formatMessage("periodOpen", from)
                    : from + "-" + event.getValidTo().format(DATE);
        });
    }

    // ------------------------------------------------------------- служебное

    private void askReason(String headerKey, String requiredKey, java.util.function.Consumer<String> action) {
        dialogs.createInputDialog(this)
                .withHeader(messageBundle.getMessage(headerKey))
                .withParameters(InputParameter.stringParameter("reason")
                        .withLabel(messageBundle.getMessage("reasonLabel"))
                        .withRequired(true))
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (!closeEvent.closedWith(DialogOutcome.OK)) {
                        return;
                    }
                    String reason = closeEvent.getValue("reason");
                    if (reason == null || reason.isBlank()) {
                        notifications.create(messageBundle.getMessage(requiredKey))
                                .withType(Notifications.Type.WARNING)
                                .show();
                        return;
                    }
                    action.accept(reason);
                })
                .open();
    }

    private void showSelectWarning() {
        notifications.create(messageBundle.getMessage("selectRequestFirst"))
                .withType(Notifications.Type.WARNING)
                .show();
    }

    private User currentUser() {
        UserDetails user = currentAuthentication.getUser();
        return user instanceof User appUser ? appUser : null;
    }


}
