package uz.kapitalbank.umida.view.reporting;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import io.jmix.core.*;
import io.jmix.core.accesscontext.SpecificOperationAccessContext;
import io.jmix.core.querycondition.Condition;
import io.jmix.core.querycondition.LogicalCondition;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.UiProperties;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.download.ByteArrayDownloadDataProvider;
import io.jmix.flowui.download.DownloadFormat;
import io.jmix.flowui.kit.action.Action;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.kit.component.dropdownbutton.DropdownButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import io.jmix.reports.ReportImportExport;
import io.jmix.reports.ReportRepository;
import io.jmix.reports.entity.Report;
import io.jmix.reports.entity.ReportGroup;
import io.jmix.reports.entity.ReportTemplate;
import io.jmix.reports.exception.MissingDefaultTemplateException;
import io.jmix.reports.util.ReportsUtils;
import io.jmix.reportsflowui.download.ReportDownloader;
import io.jmix.reportsflowui.runner.ParametersDialogShowMode;
import io.jmix.reportsflowui.runner.UiReportRunner;
import io.jmix.reportsflowui.view.importdialog.ReportImportDialogView;
import io.jmix.reportsflowui.view.reportwizard.ReportWizardCreatorView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.security.core.userdetails.UserDetails;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.UserReport;
import uz.kapitalbank.umida.entity.ReportDomain;
import uz.kapitalbank.umida.service.ReportAccessService;
import uz.kapitalbank.umida.service.UserReportSyncService;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.*;
import java.util.stream.Collectors;

@Route(value = "reporting-main-view", layout = MainView.class)
@ViewController(id = "umida_ReportingMainView")
@ViewDescriptor(path = "reporting-main-view.xml")
@DialogMode(width = "64em")
public class ReportingMainView extends StandardListView<UserReport> {

    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private DialogWindows dialogWindows;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private ReportAccessService reportAccessService;
    @Autowired
    private UiReportRunner uiReportRunner;
    @Autowired
    private Notifications notifications;
    @Autowired
    private MetadataTools metadataTools;
    @Autowired
    private EntityUuidGenerator entityUuidGenerator;
    @Autowired
    private ReportsUtils reportsUtils;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private ReportImportExport reportImportExport;
    @Autowired
    private ReportDownloader downloader;
    @Autowired
    private UiProperties uiProperties;
    @Autowired
    private CoreProperties coreProperties;
    @Autowired
    private AccessManager accessManager;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private UserReportSyncService userReportSyncService;
    @Autowired
    private Messages messages;

    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private CollectionLoader<UserReport> userReportsDl;
    @ViewComponent
    private DataGrid<UserReport> userReportsDataGrid;
    @ViewComponent
    private HorizontalLayout actionsPanel;
    @ViewComponent
    private JmixSelect<ReportScopeFilter> scopeFilterField;
    @ViewComponent
    private DropdownButton createReportDropdown;
    @ViewComponent
    private JmixButton reportAccessButton;
    @ViewComponent
    private JmixButton descriptionButton;
    @ViewComponent
    private JmixButton editReportButton;
    @ViewComponent
    private DropdownButton moreDropdown;
    @ViewComponent
    private JmixButton runReportButton;
    @ViewComponent
    private TypedTextField<String> nameFilterField;
    @ViewComponent
    private TypedTextField<String> codeFilterField;
    @ViewComponent
    private EntityPicker<ReportGroup> groupFilterField;
    @ViewComponent
    private EntityPicker<ReportDomain> domainFilterField;


    /**
     * Set while the Reset button empties the filter fields, so their value-change listeners do not
     * each trigger a reload.
     */
    private boolean resettingFilters;

    /**
     * Значения выпадающего фильтра над списком. Ключ сообщения хранится рядом со значением: набор
     * вариантов разный у владельца отчётов и у обычного сотрудника.
     */
    private enum ReportScopeFilter {
        ALL("scopeFilter.all"),
        MINE("scopeFilter.mine"),
        OTHERS("scopeFilter.others"),
        AVAILABLE("scopeFilter.available"),
        UNAVAILABLE("scopeFilter.unavailable");

        private final String messageKey;

        ReportScopeFilter(String messageKey) {
            this.messageKey = messageKey;
        }
    }

    /**
     * Specific permission that distinguishes administrators (who may create/edit/import/export
     * reports) from regular users (who may only view, run and remove their assigned reports).
     */
    public static final String MANAGE_USER_REPORTS = "userReports.manage";

    /**
     * Marks the one accented command in the toolbar; every other command stays flat. See
     * {@code reporting.css}.
     */
    private static final String PRIMARY_ACTION_CLASS = "report-primary-action";

    /**
     * Tailors the toolbar and selection behaviour to the user's permissions.
     * <p>
     * Every row-scoped command is a grid action, so hiding it removes it from the toolbar button,
     * the toolbar dropdown item and the row context menu at once. Regular users get a
     * view/run/delete-only toolbar (create/edit/copy/import/export and the admin access-request
     * journal are hidden) plus the access-request workflow: Run is enabled only for reports they
     * have approved access to and Request access covers the rest, both following the grid
     * selection. Administrators run reports directly, so that workflow is hidden for them.
     */
    @Subscribe
    public void onInit(InitEvent event) {
        // Reports created outside this view (the add-on's Reports view, the wizard, an import) have
        // no Reporting row yet, so they are linked before the first load — otherwise they would
        // never show up here.
        userReportSyncService.syncMissingReports(getCurrentUser());

        initFilterListeners();

        initScopeFilter();

        if (!isManagePermitted()) {
            // Каждая команда — действие грида, поэтому одно скрытие убирает и кнопку тулбара, и
            // пункт контекстного меню строки. «Доступы» и ведение отчётов — только у владельца.
            setActionsVisible(false, "createReport", "createReportWizard", "editReport",
                    "copyReport", "importReport", "exportReport", "removeAction");
            // Дропдауны и «Изменить» не привязаны к действиям грида целиком, поэтому скрываются
            // отдельно.
            createReportDropdown.setVisible(false);
            editReportButton.setVisible(false);
            moreDropdown.setVisible(false);
            // У обычного пользователя порядок другой: описание идёт перед доступами.
            actionsPanel.addComponentAtIndex(actionsPanel.indexOf(reportAccessButton), descriptionButton);
            // Run is the primary command for a regular user; for an administrator it is Create.
            runReportButton.addClassName(PRIMARY_ACTION_CLASS);
            // Запуск разрешён только по выданному доступу, поэтому команда следует за выбором
            // строки: без доступа она остаётся заблокированной.
            userReportsDataGrid.addSelectionListener(e -> updateRunAction());
            updateRunAction();
        } else {
            // Create is the primary command for an administrator; for a regular user it is Run.
            createReportDropdown.addClassName(PRIMARY_ACTION_CLASS);
        }
    }

    /**
     * Набор значений фильтра области видимости зависит от роли: владелец отчётов делит список на
     * свои и чужие, обычный сотрудник — на доступные ему и остальные. По умолчанию у всех выбран
     * срез «Все»: список отчётов общий, и владелец, и сотрудник видят его целиком, а сотруднику
     * доступ на запуск выдаётся отдельно — по заявке.
     */
    private void initScopeFilter() {
        boolean manage = isManagePermitted();
        ReportScopeFilter[] items = manage
                ? new ReportScopeFilter[]{ReportScopeFilter.ALL, ReportScopeFilter.MINE, ReportScopeFilter.OTHERS}
                : new ReportScopeFilter[]{ReportScopeFilter.ALL, ReportScopeFilter.AVAILABLE,
                        ReportScopeFilter.UNAVAILABLE};
        scopeFilterField.setItems(items);
        scopeFilterField.setItemLabelGenerator(item -> messageBundle.getMessage(item.messageKey));
        scopeFilterField.setEmptySelectionAllowed(false);
        scopeFilterField.setValue(defaultScope());
        scopeFilterField.addValueChangeListener(e -> reloadReports());
    }

    /**
     * Срез по умолчанию — «Все» независимо от роли: список отчётов общий. Остальные значения
     * остаются в выпадающем списке как ручное сужение.
     */
    private ReportScopeFilter defaultScope() {
        return ReportScopeFilter.ALL;
    }

    private void initFilterListeners() {
        nameFilterField.addValueChangeListener(e -> reloadReports());
        codeFilterField.addValueChangeListener(e -> reloadReports());
        // Группа и область выбираются диалогом справочника, очистка — действием «clear» самого
        // поля, поэтому здесь остаётся только перезагрузка списка.
        groupFilterField.addValueChangeListener(e -> reloadReports());
        domainFilterField.addValueChangeListener(e -> reloadReports());
    }

    @Subscribe("resetFilterButton")
    public void onResetFilterButtonClick(ClickEvent<Button> event) {
        resettingFilters = true;
        try {
            nameFilterField.clear();
            codeFilterField.clear();
            groupFilterField.clear();
            domainFilterField.clear();
            scopeFilterField.setValue(defaultScope());
        } finally {
            resettingFilters = false;
        }
        reloadReports();
    }

    /**
     * Reloads the list from the first page: the filter panel narrows the result set, so the page
     * the user was on may no longer exist. Clearing the fields one by one during a reset is
     * collapsed into the single reload that follows.
     */
    private void reloadReports() {
        if (resettingFilters) {
            return;
        }
        userReportsDl.setFirstResult(0);
        userReportsDl.load();
    }

    private void setActionsVisible(boolean visible, String... actionIds) {
        for (String actionId : actionIds) {
            Action action = userReportsDataGrid.getAction(actionId);
            if (action != null) {
                action.setVisible(visible);
            }
        }
    }

    /**
     * Держит «Выполнить» в согласии с правами на выбранный отчёт: без выбора и без доступа
     * команда заблокирована. Владельца отчётов это не касается — он запускает всё.
     */
    private void updateRunAction() {
        Action runAction = userReportsDataGrid.getAction("runReport");
        if (runAction == null) {
            return;
        }
        UserReport userReport = userReportsDataGrid.getSingleSelectedItem();
        runAction.setEnabled(userReport != null
                && reportAccessService.canRun(getCurrentUser(), userReport.getReport()));
    }

    private boolean isManagePermitted() {
        SpecificOperationAccessContext context = new SpecificOperationAccessContext(MANAGE_USER_REPORTS);
        accessManager.applyRegisteredConstraints(context);
        return context.isPermitted();
    }

    @Subscribe("userReportsDataGrid.createReport")
    public void onCreateReport(ActionPerformedEvent event) {
        DialogWindow<ExtReportDetailView> dialog = dialogWindows.detail(this, Report.class)
                .withViewClass(ExtReportDetailView.class)
                .newEntity()
                .withAfterCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(StandardOutcome.SAVE)) {
                        assignOwner(closeEvent.getView().getEditedEntity());
                        userReportsDl.load();
                    }
                })
                .build();
        applyDialogSize(dialog);
        dialog.open();
    }

    @Subscribe("userReportsDataGrid.createReportWizard")
    public void onCreateReportWizard(ActionPerformedEvent event) {
        DialogWindow<ReportWizardCreatorView> wizard = dialogWindows.view(this, ReportWizardCreatorView.class)
                .build();
        wizard.addAfterCloseListener(wizardCloseEvent -> {
            if (!wizardCloseEvent.closedWith(StandardOutcome.SAVE)) {
                return;
            }
            Report generatedReport = wizard.getView().getItem().getGeneratedReport();
            DialogWindow<ExtReportDetailView> dialog = dialogWindows.detail(this, Report.class)
                    .withViewClass(ExtReportDetailView.class)
                    .editEntity(generatedReport)
                    .withAfterCloseListener(closeEvent -> {
                        if (closeEvent.closedWith(StandardOutcome.SAVE)) {
                            assignOwner(closeEvent.getView().getEditedEntity());
                            userReportsDl.load();
                        }
                    })
                    .build();
            applyDialogSize(dialog);
            dialog.open();
        });
        applyDialogSize(wizard);
        wizard.open();
    }

    @Subscribe("userReportsDataGrid.editReport")
    public void onEditReport(ActionPerformedEvent event) {
        UserReport userReport = userReportsDataGrid.getSingleSelectedItem();
        if (userReport == null) {
            showSelectReportWarning();
            return;
        }

        DialogWindow<ExtReportDetailView> dialog = dialogWindows.detail(this, Report.class)
                .withViewClass(ExtReportDetailView.class)
                .editEntity(userReport.getReport())
                .withAfterCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(StandardOutcome.SAVE)) {
                        userReportsDl.load();
                    }
                })
                .build();
        applyDialogSize(dialog);
        dialog.open();
    }

    /**
     * Runs the selected report.
     */
    @Subscribe("userReportsDataGrid.runReport")
    public void onRunReport(ActionPerformedEvent event) {
        UserReport userReport = userReportsDataGrid.getSingleSelectedItem();
        if (userReport == null) {
            showSelectReportWarning();
            return;
        }
        Report report = userReport.getReport();
        // Кнопка у обычного пользователя и так заблокирована без доступа; проверка оставлена на
        // случай запуска из контекстного меню и как страховка от гонки с отзывом доступа.
        if (!isManagePermitted() && !reportAccessService.canRun(getCurrentUser(), report)) {
            notifications.create(messageBundle.getMessage("noAccessToRun"))
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }
        runReport(report);
    }

    /**
     * Открывает страницу «Доступы» по выбранному отчёту.
     */
    @Subscribe("userReportsDataGrid.reportAccess")
    public void onReportAccess(ActionPerformedEvent event) {
        UserReport userReport = userReportsDataGrid.getSingleSelectedItem();
        if (userReport == null) {
            showSelectReportWarning();
            return;
        }
        // Отдельная страница, а не диалог: у экрана свои вкладки и он открывается ссылкой.
        viewNavigators.view(this, ReportAccessView.class)
                .withRouteParameters(new RouteParameters("reportId", userReport.getReport().getId().toString()))
                .navigate();
    }

    /**
     * Shows what the report is for without running it: its description plus a shortcut to download
     * the default template, which is the closest thing to a sample of the report's output.
     */
    @Subscribe("userReportsDataGrid.showDescription")
    public void onShowDescription(ActionPerformedEvent event) {
        UserReport userReport = userReportsDataGrid.getSingleSelectedItem();
        if (userReport == null) {
            showSelectReportWarning();
            return;
        }
        showDescriptionDialog(userReport.getReport());
    }

    private void showDescriptionDialog(Report report) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(messageBundle.formatMessage("descriptionDialogHeader", report.getName()));
        dialog.setWidth("44em");
        dialog.setDraggable(true);
        dialog.setResizable(true);

        Button previewButton = new Button(messageBundle.getMessage("previewSampleReport"),
                VaadinIcon.FILE_TEXT_O.create());
        previewButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        previewButton.addClickListener(e -> downloadSampleTemplate(report));

        Span sectionTitle = new Span(messageBundle.getMessage("descriptionSection"));
        sectionTitle.addClassName("report-description-title");

        String description = report.getDescription();
        Div descriptionText = new Div();
        descriptionText.addClassName("report-description-text");
        if (description == null || description.isBlank()) {
            descriptionText.setText(messageBundle.getMessage("noDescription"));
            descriptionText.addClassName("report-muted");
        } else {
            descriptionText.setText(description);
        }

        VerticalLayout content = new VerticalLayout(previewButton, sectionTitle, descriptionText);
        content.addClassName("report-description-content");
        content.setPadding(false);
        content.setWidthFull();
        dialog.add(content);

        Button closeButton = new Button(messageBundle.getMessage("closeDialog"), e -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        HorizontalLayout footer = new HorizontalLayout(closeButton);
        footer.setWidthFull();
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        dialog.getFooter().add(footer);

        dialog.open();
    }

    private void downloadSampleTemplate(Report report) {
        Report loaded = dataManager.load(Id.of(report))
                .fetchPlan("report.edit")
                .one();
        ReportTemplate template = loaded.getDefaultTemplate();
        if (template == null || template.getContent() == null) {
            notifications.create(messageBundle.getMessage("noSampleAvailable"))
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }
        downloader.download(template.getContent(), template.getName());
    }

    private void showSelectReportWarning() {
        notifications.create(messageBundle.getMessage("selectReportFirst"))
                .withType(Notifications.Type.WARNING)
                .show();
    }

    private void runReport(Report report) {
        try {
            uiReportRunner.byReportEntity(report)
                    .withParametersDialogShowMode(ParametersDialogShowMode.IF_REQUIRED)
                    .runAndShow();
        } catch (MissingDefaultTemplateException e) {
            notifications.create(messageBundle.getMessage("missingDefaultTemplate"))
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    @Subscribe("userReportsDataGrid.copyReport")
    public void onCopyReport(ActionPerformedEvent event) {
        UserReport userReport = userReportsDataGrid.getSingleSelectedItem();
        if (userReport == null) {
            showSelectReportWarning();
            return;
        }
        Report copied = copyReport(userReport.getReport());
        assignOwner(copied);
        userReportsDl.load();
    }

    @Subscribe("userReportsDataGrid.importReport")
    public void onImportReport(ActionPerformedEvent event) {
        dialogWindows.view(this, ReportImportDialogView.class)
                .withAfterCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(StandardOutcome.SAVE)) {
                        userReportSyncService.syncMissingReports(getCurrentUser());
                        userReportsDl.load();
                    }
                })
                .build()
                .open();
    }

    @Subscribe("userReportsDataGrid.exportReport")
    public void onExportReport(ActionPerformedEvent event) {
        Set<Report> reports = selectedReports();
        if (reports.isEmpty()) {
            showSelectReportWarning();
            return;
        }

        ByteArrayDownloadDataProvider provider = new ByteArrayDownloadDataProvider(
                reportImportExport.exportReports(reports),
                uiProperties.getSaveExportedByteArrayDataThresholdBytes(),
                coreProperties.getTempDir());
        if (reports.size() > 1) {
            downloader.download(provider, "Reports", DownloadFormat.ZIP);
        } else {
            downloader.download(provider, reports.iterator().next().getName(), DownloadFormat.ZIP);
        }
    }

    /**
     * Журнал запусков смотрят по одному отчёту, поэтому окно открывается только на выбранной
     * строке и сразу ограничивается её отчётом.
     */
    @Subscribe("userReportsDataGrid.executions")
    public void onExecutions(ActionPerformedEvent event) {
        UserReport userReport = userReportsDataGrid.getSingleSelectedItem();
        if (userReport == null) {
            showSelectReportWarning();
            return;
        }
        DialogWindow<ReportExecutionHistoryView> dialog = dialogWindows.view(this, ReportExecutionHistoryView.class).build();
        dialog.getView().setReport(userReport.getReport());
        applyDialogSize(dialog);
        dialog.open();
    }

    private Set<Report> selectedReports() {
        return userReportsDataGrid.getSelectedItems().stream()
                .map(UserReport::getReport)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void applyDialogSize(DialogWindow<?> dialog) {
        dialog.setWidth("80%");
        dialog.setHeight("100%");
    }

    private Report copyReport(Report source) {
        source = dataManager.load(Id.of(source))
                .fetchPlan("report.edit")
                .one();
        Report copiedReport = metadataTools.deepCopy(source);
        copiedReport.setId(entityUuidGenerator.generate());
        copiedReport.setName(reportsUtils.generateReportName(source.getName()));
        copiedReport.setCode(null);
        for (ReportTemplate copiedTemplate : copiedReport.getTemplates()) {
            copiedTemplate.setId(entityUuidGenerator.generate());
        }
        reportRepository.save(copiedReport);
        return copiedReport;
    }

    /**
     * Заводит строку «Отчётности» для только что созданного отчёта.
     * <p>
     * Через сервис, а не напрямую: строку мог уже создать экран отчёта, сохраняя домен, и второй
     * строки на тот же отчёт быть не должно.
     */
    private void assignOwner(Report report) {
        userReportSyncService.linkReport(report.getId(), getCurrentUser());
    }

    private User getCurrentUser() {
        UserDetails userDetails = currentAuthentication.getUser();
        if (!(userDetails instanceof User user)) {
            throw new IllegalStateException("Current user must be application User");
        }
        return user;
    }

    /**
     * The WHERE clause built from the filter panel together with its parameter values, so the list
     * and the total-count queries stay in sync.
     */
    private record FilterQuery(String where, Map<String, Object> parameters) {
    }

    /**
     * Translates the filter panel into a JPQL condition. Returns {@code null} when the result is
     * known to be empty without querying, i.e. the "available reports" filter is on but no report
     * has been approved for the user yet.
     */
    @Nullable
    private FilterQuery buildFilterQuery() {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> parameters = new LinkedHashMap<>();

        String name = trimToNull(nameFilterField.getValue());
        if (name != null) {
            conditions.add("lower(e.report.name) like :nameFilter");
            parameters.put("nameFilter", "%" + name.toLowerCase() + "%");
        }

        String code = trimToNull(codeFilterField.getValue());
        if (code != null) {
            conditions.add("lower(e.report.code) like :codeFilter");
            parameters.put("codeFilter", "%" + code.toLowerCase() + "%");
        }

        ReportGroup group = groupFilterField.getValue();
        if (group != null) {
            conditions.add("e.report.group = :groupFilter");
            parameters.put("groupFilter", group);
        }

        ReportDomain domain = domainFilterField.getValue();
        if (domain != null) {
            conditions.add("e.domain = :domainFilter");
            parameters.put("domainFilter", domain);
        }

        ReportScopeFilter scope = scopeFilterField.getValue();
        if (scope == ReportScopeFilter.MINE || scope == ReportScopeFilter.OTHERS) {
            conditions.add(scope == ReportScopeFilter.MINE ? "e.owner = :currentOwner" : "e.owner <> :currentOwner");
            parameters.put("currentOwner", getCurrentUser());
        } else if (scope == ReportScopeFilter.AVAILABLE || scope == ReportScopeFilter.UNAVAILABLE) {
            Set<UUID> availableIds = reportAccessService.availableReportIds(getCurrentUser());
            if (availableIds.isEmpty()) {
                // Доступов нет: «Доступные мне» пусты, «Без доступа» — это весь список.
                if (scope == ReportScopeFilter.AVAILABLE) {
                    return null;
                }
            } else {
                conditions.add(scope == ReportScopeFilter.AVAILABLE
                        ? "e.report.id in :availableReportIds"
                        : "e.report.id not in :availableReportIds");
                parameters.put("availableReportIds", availableIds);
            }
        }

        String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
        return new FilterQuery(where, parameters);
    }

    @Nullable
    private static String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * The condition the grid's column header filters put on the loader, or an empty one when no
     * header filter is active. It has to be passed through by hand because this loader builds its
     * own query.
     */
    private static Condition headerFilterCondition(@Nullable DataLoadContextQuery query) {
        Condition condition = query instanceof LoadContext.Query loadContextQuery
                ? loadContextQuery.getCondition()
                : null;
        return condition != null ? condition : LogicalCondition.and();
    }

    /**
     * Loads the Reporting list, a shared list where every user sees all reports added to it,
     * narrowed by the filter panel and by the column header filters. Column sorting is honoured;
     * without it the most recently updated reports come first.
     */
    @Install(to = "userReportsDl", target = Target.DATA_LOADER)
    private List<UserReport> userReportsDlLoadDelegate(LoadContext<UserReport> loadContext) {
        FilterQuery filterQuery = buildFilterQuery();
        if (filterQuery == null) {
            return List.of();
        }

        Sort sort = loadContext.getQuery().getSort();
        if (sort == null || sort.getOrders().isEmpty()) {
            sort = Sort.by(Sort.Order.desc("report.updateTs"), Sort.Order.asc("report.name"));
        }

        return dataManager.load(UserReport.class)
                .query("select e from UserReport e" + filterQuery.where())
                .condition(headerFilterCondition(loadContext.getQuery()))
                .parameters(filterQuery.parameters())
                .sort(sort)
                .fetchPlan(loadContext.getFetchPlan())
                .firstResult(loadContext.getQuery().getFirstResult())
                .maxResults(loadContext.getQuery().getMaxResults())
                .list();
    }

    @Install(to = "pagination", subject = "totalCountDelegate")
    private Integer paginationTotalCountDelegate(DataLoadContext context) {
        FilterQuery filterQuery = buildFilterQuery();
        if (filterQuery == null) {
            return 0;
        }

        Condition condition = context instanceof LoadContext<?> loadContext
                ? headerFilterCondition(loadContext.getQuery())
                : LogicalCondition.and();

        if (condition instanceof LogicalCondition logical && logical.getConditions().isEmpty()) {
            FluentValueLoader<Long> loader = dataManager.loadValue(
                    "select count(e) from UserReport e" + filterQuery.where(), Long.class);
            filterQuery.parameters().forEach(loader::parameter);
            return loader.one().intValue();
        }

        // A header filter is active: a value loader cannot take a Condition, so the matching rows
        // are counted instead. The Reporting holds one row per report, so this stays small.
        return dataManager.load(UserReport.class)
                .query("select e from UserReport e" + filterQuery.where())
                .condition(condition)
                .parameters(filterQuery.parameters())
                .fetchPlan(FetchPlan.INSTANCE_NAME)
                .list()
                .size();
    }
}
