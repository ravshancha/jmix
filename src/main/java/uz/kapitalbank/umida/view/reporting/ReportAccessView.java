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
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.component.textfield.TypedTextField;
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
import uz.kapitalbank.umida.entity.ReportSubdivisionAccess;
import uz.kapitalbank.umida.entity.ReportSubdivisionRow;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.OrgStructureSubdivision;
import uz.kapitalbank.umida.enums.AccessLevel;
import uz.kapitalbank.umida.service.ReportAccessService;
import uz.kapitalbank.umida.view.main.MainView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Экран «Доступы» по отчёту. Открывается из списка отчётов — кнопкой или пунктом контекстного
 * меню строки — и показывает разный набор вкладок в зависимости от прав.
 * <p>
 * Обычный сотрудник видит «Доступы» (у кого можно попросить) и «Историю» по себе. Владелец отчёта,
 * сотрудники подразделения-владельца и обладатель уровня GRANT дополнительно получают «Все» (кто и что имеет),
 * «Подразделения» (доступ подразделениям целиком) и «Запросы» со счётчиком нерешённых заявок.
 * <p>
 * Распорядитель доступов — владелец, сотрудник подразделения-владельца, администратор — видит на вкладке «Все»
 * всех пользователей приложения и выдаёт, настраивает и закрывает доступ сам, без заявки; он же
 * управляет доступами подразделений.
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
    private CollectionContainer<ReportSubdivisionRow> subdivisionRowsDc;
    @ViewComponent
    private TreeDataGrid<ReportSubdivisionRow> subdivisionsDataGrid;
    @ViewComponent
    private TypedTextField<String> subdivisionFilterField;
    @ViewComponent
    private JmixButton grantSubdivisionButton;
    @ViewComponent
    private JmixButton editSubdivisionButton;
    @ViewComponent
    private JmixButton revokeSubdivisionButton;
    @ViewComponent
    private CollectionContainer<ReportAccessEvent> historyDc;
    @ViewComponent
    private JmixButton requestAccessButton;
    @ViewComponent
    private JmixButton grantAccessButton;
    @ViewComponent
    private JmixButton editAccessButton;
    @ViewComponent
    private JmixButton revokeAccessButton;
    @ViewComponent
    private JmixButton approveButton;
    @ViewComponent
    private JmixButton rejectButton;

    private MainView headerHost;
    private Report report;
    private boolean decisionMaker;
    /**
     * Распорядитель доступов — владелец отчёта, сотрудник подразделения-владельца или администратор: только они
     * выдают доступ без заявки, настраивают и закрывают его, в том числе подразделениям. Делегату
     * с уровнем GRANT это недоступно.
     */
    private boolean manager;
    /**
     * Всё дерево подразделений: поиск сужает показ, а не перечитывает справочник.
     */
    private List<ReportSubdivisionRow> subdivisionRows = List.of();

    @Subscribe
    public void onInit(InitEvent event) {
        // Кнопка «Запросить доступ» становится акцентной только когда выбран адресат — как в дизайне.
        grantorsDataGrid.addSelectionListener(selection -> updateRequestButton());
        allDataGrid.addSelectionListener(selection -> updateAllTabButtons());
        requestsDataGrid.addSelectionListener(selection -> updateDecisionButtons());
        subdivisionsDataGrid.addSelectionListener(selection -> updateSubdivisionButtons());
        subdivisionFilterField.addValueChangeListener(e -> showSubdivisions());

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
        manager = reportAccessService.canManage(currentUser(), report);
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
        revokeAccessButton.setVisible(manager);
        editAccessButton.setVisible(manager);
        grantSubdivisionButton.setVisible(manager);
        editSubdivisionButton.setVisible(manager);
        revokeSubdivisionButton.setVisible(manager);
        // Владельцу и администратору доступ полный по определению — просить им нечего.
        requestAccessButton.setVisible(!manager);
        updateAllTabButtons();
        updateSubdivisionButtons();
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
            // Распорядителю — все пользователи, чтобы выдавать доступ без заявки; делегату с
            // уровнем GRANT — только те, у кого доступ есть или ожидается.
            allRowsDc.setItems(manager
                    ? reportAccessService.allUserRows(report)
                    : reportAccessService.accessRows(report));
            subdivisionRows = reportAccessService.subdivisionRows(report);
            showSubdivisions();
            requestsDc.setItems(reportAccessService.pendingRequests(report));
            historyDc.setItems(reportAccessService.history(report, null));
            updateRequestsBadge();
        } else {
            historyDc.setItems(reportAccessService.history(report, currentUser()));
        }
        grantorRowsDc.setItems(reportAccessService.grantorRows(report, currentUser()));
        updateRequestButton();
        updateAllTabButtons();
        updateDecisionButtons();
        updateSubdivisionButtons();
    }

    /**
     * Кнопки вкладки «Все» смотрят на выбранную строку: без выбора они неактивны. На себе они
     * тоже заблокированы: свой доступ не выдают, не настраивают и не закрывают — владелец иначе
     * отрезал бы себя от собственного отчёта. Закрывать нечего и на строке владельца: доступа как
     * строки у него нет, он есть по определению. Не закрывается и доступ администратора: он и без
     * строки видит всё, закрытие ничего бы не изменило.
     * <p>
     * У распорядителя «Дать доступ» активна на сотруднике без личного доступа, «Настроить» — на
     * сотруднике с ним. Владельцу и подразделению-владельцу доступ не выдают: он у них по определению.
     */
    private void updateAllTabButtons() {
        ReportAccessRow row = allDataGrid.getSingleSelectedItem();
        boolean self = isSelf(row);
        boolean other = row != null && !self;
        if (!manager) {
            grantAccessButton.setEnabled(other);
            editAccessButton.setEnabled(false);
            revokeAccessButton.setEnabled(false);
            return;
        }
        boolean grantable = other && row.getAccessLevel() != AccessLevel.OWNER
                && row.getAccessLevel() != AccessLevel.SUBDIVISION_OWNER;
        grantAccessButton.setEnabled(grantable && row.getAccessId() == null);
        editAccessButton.setEnabled(grantable && row.getAccessId() != null);
        revokeAccessButton.setEnabled(grantable && row.getAccessId() != null
                && !reportAccessService.holdsAdministration(reportAccessService.user(row.getUserId())));
    }

    /**
     * «Дать доступ» — подразделению без своего доступа, «Настроить» и «Закрыть» — с ним.
     */
    private void updateSubdivisionButtons() {
        ReportSubdivisionRow row = manager ? subdivisionsDataGrid.getSingleSelectedItem() : null;
        grantSubdivisionButton.setEnabled(row != null && row.getAccessId() == null);
        editSubdivisionButton.setEnabled(row != null && row.getAccessId() != null);
        revokeSubdivisionButton.setEnabled(row != null && row.getAccessId() != null);
    }

    /**
     * Показывает дерево целиком либо, при поиске, найденные подразделения вместе с цепочкой
     * вышестоящих — иначе дереву не к чему их прицепить. Найденное раскрывается. Выбранная строка
     * сохраняется и после перечитывания.
     */
    private void showSubdivisions() {
        ReportSubdivisionRow selected = subdivisionsDataGrid.getSingleSelectedItem();
        String filter = subdivisionFilterField.getValue() == null
                ? "" : subdivisionFilterField.getValue().trim().toLowerCase();
        List<ReportSubdivisionRow> shown = subdivisionRows;
        if (!filter.isEmpty()) {
            Set<ReportSubdivisionRow> visible = new LinkedHashSet<>();
            for (ReportSubdivisionRow row : subdivisionRows) {
                if (row.getName().toLowerCase().contains(filter)) {
                    // Вверх по цепочке до первого уже добавленного — выше него всё уже есть.
                    ReportSubdivisionRow up = row;
                    while (up != null && visible.add(up)) {
                        up = up.getParent();
                    }
                }
            }
            shown = subdivisionRows.stream().filter(visible::contains).toList();
        }
        subdivisionRowsDc.setItems(shown);
        if (!filter.isEmpty()) {
            subdivisionsDataGrid.expand(shown);
        }
        if (selected != null) {
            shown.stream()
                    .filter(row -> row.getId().equals(selected.getId()))
                    .findFirst()
                    .ifPresent(subdivisionsDataGrid::select);
        }
    }

    /**
     * «Одобрить» и «Отклонить» решают выбранную заявку, поэтому без выбора они неактивны.
     */
    private void updateDecisionButtons() {
        boolean selected = requestsDataGrid.getSingleSelectedItem() != null;
        approveButton.setEnabled(selected);
        rejectButton.setEnabled(selected);
    }

    private boolean isSelf(@Nullable ReportAccessRow row) {
        User current = currentUser();
        return row != null && current != null && current.getId().equals(row.getUserId());
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
     * Закрыть выданный доступ вправе владелец отчёта и администратор: делегат с уровнем GRANT
     * доступ выдаёт, но не отзывает.
     */
    @Subscribe("revokeAccessButton")
    public void onRevokeAccess(ClickEvent<Button> event) {
        if (!manager) {
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
        ReportAccessRow row = allDataGrid.getSingleSelectedItem();
        if (!manager || row == null || row.getAccessId() == null || isSelf(row)) {
            return;
        }
        openUserGrantDialog(row, reportAccessService.access(row.getAccessId()));
    }

    /**
     * Распорядитель выдаёт доступ сразу, без заявки. Делегату с уровнем GRANT доступ по-прежнему
     * выдаётся только по заявке — со вкладки «Запросы».
     */
    @Subscribe("grantAccessButton")
    public void onGrantAccess(ClickEvent<Button> event) {
        ReportAccessRow row = allDataGrid.getSingleSelectedItem();
        if (row == null || isSelf(row)) {
            return;
        }
        if (!manager) {
            notifications.create(messageBundle.getMessage("grantAccessFromRequests"))
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }
        openUserGrantDialog(row, null);
    }

    private void openUserGrantDialog(ReportAccessRow row, @Nullable ReportAccess current) {
        var dialog = dialogWindows.view(this, GrantAccessDialogView.class).build();
        dialog.getView().setUser(report, reportAccessService.user(row.getUserId()), current);
        dialog.addAfterCloseListener(e -> reloadAll());
        dialog.open();
    }

    // ------------------------------------------------------------- подразделения

    /**
     * Доступ выдаётся прямо из дерева, без диалога: с сегодняшнего дня и бессрочно. Срок, если
     * нужен, задаётся потом кнопкой «Настроить».
     */
    @Subscribe("grantSubdivisionButton")
    public void onGrantSubdivision(ClickEvent<Button> event) {
        ReportSubdivisionRow row = subdivisionsDataGrid.getSingleSelectedItem();
        if (!manager || row == null || row.getAccessId() != null) {
            return;
        }
        reportAccessService.grantSubdivision(report, dataManager.getReference(OrgStructureSubdivision.class, row.getId()),
                currentUser(), LocalDate.now(), null);
        notifications.create(messageBundle.getMessage("accessGranted")).show();
        reloadAll();
    }

    /**
     * Срок доступа подразделения меняется в диалоге выдачи.
     */
    @Subscribe("editSubdivisionButton")
    public void onEditSubdivision(ClickEvent<Button> event) {
        ReportSubdivisionRow row = subdivisionsDataGrid.getSingleSelectedItem();
        if (!manager || row == null || row.getAccessId() == null) {
            return;
        }
        ReportSubdivisionAccess access = reportAccessService.subdivisionAccess(row.getAccessId());
        var dialog = dialogWindows.view(this, GrantAccessDialogView.class).build();
        dialog.getView().setSubdivision(report, access.getSubdivision(), access);
        dialog.addAfterCloseListener(e -> reloadAll());
        dialog.open();
    }

    @Subscribe("revokeSubdivisionButton")
    public void onRevokeSubdivision(ClickEvent<Button> event) {
        ReportSubdivisionRow row = subdivisionsDataGrid.getSingleSelectedItem();
        if (!manager || row == null || row.getAccessId() == null) {
            return;
        }
        askReason("revokeDialogHeader", "revokeReasonRequired",
                reason -> {
                    reportAccessService.revokeSubdivision(reportAccessService.subdivisionAccess(row.getAccessId()),
                            currentUser(), reason);
                    notifications.create(messageBundle.getMessage("accessRevoked")).show();
                    reloadAll();
                });
    }

    /**
     * «Права доступа» подразделения: свой доступ либо вышестоящее, через которое он уже есть.
     */
    @Supply(to = "subdivisionsDataGrid.subdivisionAccess", subject = "renderer")
    private com.vaadin.flow.data.renderer.Renderer<ReportSubdivisionRow> subdivisionAccessRenderer() {
        return new com.vaadin.flow.data.renderer.TextRenderer<>(row -> {
            if (row.getAccessId() != null) {
                return messageBundle.getMessage("subdivisionAccess.own");
            }
            return row.getInheritedFrom() == null
                    ? "" : messageBundle.formatMessage("subdivisionAccess.inherited", row.getInheritedFrom());
        });
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
     * «Пользователь» в журнале: сотрудник, а у событий по доступу подразделения — подразделение.
     */
    @Supply(to = "historyDataGrid.subjectName", subject = "renderer")
    private com.vaadin.flow.data.renderer.Renderer<ReportAccessEvent> subjectNameRenderer() {
        return new com.vaadin.flow.data.renderer.TextRenderer<>(event -> {
            if (event.getSubjectUser() != null) {
                return event.getSubjectUser().getDisplayName();
            }
            OrgStructureSubdivision subdivision = event.getSubdivision();
            return subdivision == null ? "" : messageBundle.formatMessage("subdivisionSubject",
                    subdivision.getName() == null ? subdivision.getId() : subdivision.getName());
        });
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
