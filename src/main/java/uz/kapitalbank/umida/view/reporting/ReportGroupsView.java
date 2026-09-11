package uz.kapitalbank.umida.view.reporting;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.data.selection.SelectionEvent;
import com.vaadin.flow.router.Route;
import io.jmix.core.LoadContext;
import io.jmix.core.Messages;
import io.jmix.core.MetadataTools;
import io.jmix.core.Sort;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.action.list.CreateAction;
import io.jmix.flowui.action.list.EditAction;
import io.jmix.flowui.action.list.RemoveAction;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.kit.action.Action;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.DialogWindow;
import io.jmix.flowui.view.Install;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.MessageBundle;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.Supply;
import io.jmix.flowui.view.Target;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.jmix.reports.ReportGroupFilter;
import io.jmix.reports.ReportGroupLoadContext;
import io.jmix.reports.ReportGroupRepository;
import io.jmix.reports.ReportRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.router.RouteParameters;
import io.jmix.core.AccessManager;
import io.jmix.core.DataManager;
import io.jmix.core.Id;
import io.jmix.core.accesscontext.SpecificOperationAccessContext;
import io.jmix.reports.entity.Report;
import io.jmix.reports.entity.ReportGroup;
import io.jmix.reports.entity.ReportSource;
import io.jmix.reports.entity.ReportTemplate;
import io.jmix.reports.exception.MissingDefaultTemplateException;
import io.jmix.reportsflowui.runner.ParametersDialogShowMode;
import io.jmix.reportsflowui.runner.UiReportRunner;
import io.jmix.reportsflowui.view.group.ReportGroupDetailView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import io.jmix.core.security.CurrentAuthentication;
import uz.kapitalbank.umida.entity.ExtReportGroup;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.service.ReportAccessService;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.List;
import java.util.Map;

/**
 * Группы отчётов внутри раздела «Отчетность». Повторяет экран {@code report_ReportGroup.list}
 * аддона: те же фильтры, те же действия и тот же редактор группы — отличается только тем, что
 * живёт в нашем меню, а не в меню аддона.
 * <p>
 * Список рисуется карточками. Грид при этом никуда не делся, а спрятан и работает моделью
 * выделения: на нём объявлены действия create/edit/remove и через него экран отдаёт выбранную
 * группу в lookup-режиме. Карточка по клику просто переключает в гриде выбранную строку, поэтому
 * логика действий, правила доступности и редактор группы остались нетронутыми.
 * <p>
 * Группы приходят уже расширенными: {@link ExtReportGroup} подменяет {@code ReportGroup} в
 * метаданных, поэтому и репозиторий аддона, и контейнеры отдают именно её — отсюда приведение
 * типа без проверок.
 */
@Route(value = "report-groups", layout = MainView.class)
@ViewController(id = "umida_ReportingGroupsView")
@ViewDescriptor(path = "report-groups-view.xml")
@LookupComponent("reportGroupsDataGrid")
public class ReportGroupsView extends StandardListView<ReportGroup> {

    @ViewComponent
    protected DataGrid<ReportGroup> reportGroupsDataGrid;
    @ViewComponent
    protected Div cardsBox;
    @ViewComponent
    protected Div breadcrumbBox;
    @ViewComponent
    protected CollectionContainer<ReportGroup> groupsDc;
    @ViewComponent
    protected JmixSelect<GroupSort> sortSelect;
    @ViewComponent("reportGroupsDataGrid.remove")
    protected RemoveAction<ReportGroup> removeAction;
    @ViewComponent("reportGroupsDataGrid.edit")
    protected EditAction<ReportGroup> editAction;
    @ViewComponent("reportGroupsDataGrid.create")
    protected CreateAction<ReportGroup> createAction;
    @ViewComponent
    protected CollectionLoader<ReportGroup> groupsDl;
    @ViewComponent
    protected MessageBundle messageBundle;

    @Autowired
    protected Notifications notifications;
    @Autowired
    protected ReportGroupRepository reportGroupRepository;
    @Autowired
    protected Messages messages;
    @Autowired
    protected ReportRepository reportRepository;
    @Autowired
    protected MetadataTools metadataTools;
    @Autowired
    protected DataManager dataManager;
    @Autowired
    protected DialogWindows dialogWindows;
    @Autowired
    protected ViewNavigators viewNavigators;
    @Autowired
    protected AccessManager accessManager;
    @Autowired
    protected UiReportRunner uiReportRunner;
    @Autowired
    protected ReportAccessService reportAccessService;
    @Autowired
    protected Downloader downloader;
    @Autowired
    protected CurrentAuthentication currentAuthentication;

    /**
     * Карточка на группу: нужна, чтобы после смены выделения подсветить нужную, не перерисовывая
     * весь список.
     */
    protected final Map<ReportGroup, Div> cardsByGroup = new LinkedHashMap<>();

    /**
     * Путь, по которому пользователь провалился внутрь групп: последний элемент — открытая
     * сейчас группа, пустой путь — верхний уровень. Хранится списком, потому что по нему же
     * строятся «хлебные крошки».
     */
    protected final List<ExtReportGroup> path = new ArrayList<>();

    /**
     * Выбранная карточка отчёта. Отчёты не живут в гриде — он модель выделения только для групп,
     * поэтому их выбор держится здесь и гасится, как только выделяют группу.
     */
    protected Div selectedReportCard;

    /**
     * Код группы по умолчанию, которую заводит сам аддон: единственная, которую нельзя удалять.
     */
    protected static final String DEFAULT_GROUP_CODE = "ReportGroup.default";

    /**
     * Порядок карточек. Ключ сообщения хранится рядом со значением, сам порядок — готовым
     * {@link Sort}, который уходит в репозиторий групп.
     */
    protected enum GroupSort {
        TITLE_ASC("sort.titleAsc", Sort.by(Sort.Order.asc(ReportGroupLoadContext.LOCALIZED_TITLE_SORT_KEY))),
        TITLE_DESC("sort.titleDesc", Sort.by(Sort.Order.desc(ReportGroupLoadContext.LOCALIZED_TITLE_SORT_KEY))),
        NEWEST("sort.newest", Sort.by(Sort.Order.desc("createTs"))),
        OLDEST("sort.oldest", Sort.by(Sort.Order.asc("createTs")));

        private final String messageKey;
        private final Sort sort;

        GroupSort(String messageKey, Sort sort) {
            this.messageKey = messageKey;
            this.sort = sort;
        }
    }

    @Subscribe
    public void onInit(final InitEvent event) {
        initSortSelect();

        // Клик по пустому месту между карточками снимает выделение. Фильтр на клиенте нужен,
        // чтобы событие не срабатывало на кликах по самим карточкам — они всплывают сюда же.
        cardsBox.getElement()
                .addEventListener("click", event1 -> {
                    reportGroupsDataGrid.deselectAll();
                    clearReportSelection();
                })
                .setFilter("event.target === event.currentTarget");
    }

    protected void initSortSelect() {
        sortSelect.setItems(GroupSort.values());
        sortSelect.setItemLabelGenerator(item -> messageBundle.getMessage(item.messageKey));
        sortSelect.setEmptySelectionAllowed(false);
        sortSelect.setValue(GroupSort.TITLE_ASC);
        // Порядок меняет только сортировку, поэтому список перечитывается с первой страницы.
        sortSelect.addValueChangeListener(ev -> refreshGrid());
    }

    /**
     * Порядок, выбранный в поле над списком. До первой отрисовки поле пустое — тогда действует
     * сортировка по названию, как в исходном экране аддона.
     */
    protected Sort sortOrder() {
        GroupSort value = sortSelect.getValue();
        return (value == null ? GroupSort.TITLE_ASC : value).sort;
    }

    /**
     * Список перерисовывается на каждую загрузку: групп на экране немного (одна страница
     * пагинации), поэтому проще собрать карточки заново, чем сверять их с содержимым контейнера.
     */
    @Subscribe(id = "groupsDc", target = Target.DATA_CONTAINER)
    public void onGroupsDcCollectionChange(
            final CollectionContainer.CollectionChangeEvent<ReportGroup> event) {
        renderCards();
    }

    protected void renderCards() {
        renderBreadcrumb();
        updateActionsVisible();

        selectedReportCard = null;
        cardsBox.removeAll();
        cardsByGroup.clear();

        // Внутри группы показываются её подгруппы и её отчёты; на верхнем уровне — группы,
        // у которых родителя нет.
        List<ReportGroup> groups = new ArrayList<>(groupsDc.getItems());
        List<Report> reports = currentGroup() == null ? List.of() : reportsOf(currentGroup());

        if (groups.isEmpty() && reports.isEmpty()) {
            Span empty = new Span(messageBundle.getMessage(
                    currentGroup() == null ? "noGroups" : "emptyGroup"));
            empty.addClassName("report-group-empty");
            cardsBox.add(empty);
            return;
        }

        for (ReportGroup group : groups) {
            Div card = createGroupCard((ExtReportGroup) group);
            cardsByGroup.put(group, card);
            cardsBox.add(card);
        }
        for (Report report : reports) {
            cardsBox.add(createReportCard(report));
        }
        highlightSelected();
    }

    protected Div createGroupCard(final ExtReportGroup group) {
        Div card = createCard(ReportGroupStyles.colorOf(group),
                ReportGroupStyles.iconOf(group),
                metadataTools.getInstanceName(group));

        // Одиночный клик выбирает группу, двойной проваливается внутрь неё — содержимое
        // экрана меняется на подгруппы и отчёты, отдельного окна нет.
        card.addClickListener(event -> {
            reportGroupsDataGrid.select(group);
            if (event.getClickCount() > 1) {
                openGroup(group);
            }
        });
        card.getElement().addEventListener("keydown", event -> reportGroupsDataGrid.select(group))
                .setFilter("event.key === 'Enter' || event.key === ' '");
        addGroupContextMenu(card, group);
        return card;
    }

    /**
     * Меню правой кнопки на карточке группы: переход внутрь, а у владельца отчётов — ещё и
     * ведение групп (создание, правка, удаление). Пункт всегда работает с той группой, на
     * которой вызвано меню, поэтому она сначала выделяется.
     */
    protected void addGroupContextMenu(final Div card, final ExtReportGroup group) {
        ContextMenu menu = new ContextMenu(card);
        menu.addClassName("report-context-menu");

        // То же, что двойной клик по карточке: экран проваливается внутрь группы.
        addMenuItem(menu, "actions.openGroup", VaadinIcon.FOLDER_OPEN, true, () -> openGroup(group));
        if (!isManagePermitted()) {
            // Обычному сотруднику остаётся только навигация по каталогу.
            return;
        }
        addMenuItem(menu, "actions.create", VaadinIcon.PLUS, true, () -> {
            reportGroupsDataGrid.select(group);
            createAction.execute();
        });
        addMenuItem(menu, "actions.edit", VaadinIcon.PENCIL, true, () -> {
            reportGroupsDataGrid.select(group);
            editAction.execute();
        });
        // Группы из аннотаций в базе не лежат — удалять там нечего.
        addMenuItem(menu, "actions.remove", VaadinIcon.TRASH,
                group.getSource() != ReportSource.ANNOTATED_CLASS, () -> {
                    reportGroupsDataGrid.select(group);
                    removeSelectedGroup();
                });
    }

    /**
     * Меню правой кнопки на карточке отчёта: запуск, история запусков, описание и доступы —
     * в том же порядке, что на панели отчёта в «Отчётности». Владельцу отчётов сразу после
     * запуска добавляется правка отчёта.
     */
    protected void addReportContextMenu(final Div card, final Report report) {
        ContextMenu menu = new ContextMenu(card);
        menu.addClassName("report-context-menu");

        // Порядок как на панели отчёта: запуск, правка (у владельца), история, описание, доступы.
        // Запуск без выданного доступа заблокирован, а подсказка говорит, что доступ берут
        // через «Доступы» — иначе пункт просто молча не работал бы.
        boolean canRun = canRun(report);
        MenuItem runItem = addMenuItem(menu, "actions.run", VaadinIcon.PLAY, canRun,
                () -> runReport(report));
        if (!canRun) {
            runItem.getElement().setAttribute("title", messageBundle.getMessage("noAccessToRunHint"));
        }
        if (isManagePermitted()) {
            addMenuItem(menu, "actions.editReport", VaadinIcon.PENCIL, true, () -> editReport(report));
        }
        addMenuItem(menu, "actions.executions", VaadinIcon.CLOCK, true, () -> openExecutions(report));
        addMenuItem(menu, "actions.description", VaadinIcon.INFO_CIRCLE_O, true,
                () -> showDescriptionDialog(report));
        addMenuItem(menu, "actions.reportAccess", VaadinIcon.KEY, true, () -> openReportAccess(report));
    }

    protected MenuItem addMenuItem(final ContextMenu menu, final String messageKey, final VaadinIcon icon,
                                   final boolean enabled, final Runnable handler) {
        Icon itemIcon = icon.create();
        itemIcon.addClassName("report-context-menu-icon");
        MenuItem item = menu.addItem(itemIcon, event -> handler.run());
        item.add(messageBundle.getMessage(messageKey));
        item.setEnabled(enabled);
        return item;
    }

    /**
     * Запуск отчёта. Обычный сотрудник запускает только то, на что ему выдали доступ, — та же
     * проверка, что и в «Отчётности».
     */
    protected void runReport(final Report report) {
        if (!canRun(report)) {
            notifications.create(messageBundle.getMessage("noAccessToRun"))
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }
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

    /**
     * Описание отчёта и образец результата — то же окно, что и в «Отчётности».
     */
    protected void showDescriptionDialog(final Report report) {
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
        dialog.getFooter().add(closeButton);
        dialog.open();
    }

    protected void downloadSampleTemplate(final Report report) {
        Report loaded = dataManager.load(Id.of(report)).fetchPlan("report.edit").one();
        ReportTemplate template = loaded.getDefaultTemplate();
        if (template == null || template.getContent() == null) {
            notifications.create(messageBundle.getMessage("noSampleAvailable"))
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }
        downloader.download(template.getContent(), template.getName());
    }

    protected void editReport(final Report report) {
        DialogWindow<ExtReportDetailView> dialog = dialogWindows.detail(this, Report.class)
                .withViewClass(ExtReportDetailView.class)
                .editEntity(report)
                .withAfterCloseListener(event -> refreshGrid())
                .build();
        dialog.setWidth("80%");
        dialog.setHeight("100%");
        dialog.open();
    }

    protected void openReportAccess(final Report report) {
        viewNavigators.view(this, ReportAccessView.class)
                .withRouteParameters(new RouteParameters("reportId", report.getId().toString()))
                .navigate();
    }

    protected void openExecutions(final Report report) {
        DialogWindow<ReportExecutionHistoryView> dialog =
                dialogWindows.view(this, ReportExecutionHistoryView.class).build();
        dialog.getView().setReport(report);
        dialog.open();
    }

    /**
     * Может ли текущий пользователь запустить отчёт: владелец запускает всё, сотруднику нужен
     * выданный доступ.
     */
    protected boolean canRun(final Report report) {
        return isManagePermitted() || reportAccessService.canRun(currentUser(), report);
    }

    protected User currentUser() {
        return (User) currentAuthentication.getUser();
    }

    protected boolean isManagePermitted() {
        SpecificOperationAccessContext context = new SpecificOperationAccessContext(ReportingMainView.MANAGE_USER_REPORTS);
        accessManager.applyRegisteredConstraints(context);
        return context.isPermitted();
    }

    /**
     * Отчёт группы. Карточка не выделяется: действия экрана работают с группами, а отчёт здесь
     * показывает только состав группы.
     */
    protected Div createReportCard(final Report report) {
        Div card = createCard("var(--lumo-contrast-30pct)",
                ReportGroupStyles.REPORT_ICON, report.getName());
        card.addClassName("report-group-card-report");

        // Клик выделяет карточку — как у группы; двойной запускает отчёт, это его основное
        // действие (у группы на двойном клике переход внутрь).
        card.addClickListener(event -> {
            selectReportCard(card);
            if (event.getClickCount() > 1) {
                runReport(report);
            }
        });
        card.getElement().addEventListener("keydown", event -> selectReportCard(card))
                .setFilter("event.key === 'Enter' || event.key === ' '");

        addReportContextMenu(card, report);
        return card;
    }

    /**
     * Выделяет карточку отчёта: выделение группы при этом снимается, чтобы подсвеченной на
     * экране всегда оставалась одна карточка.
     */
    protected void selectReportCard(final Div card) {
        reportGroupsDataGrid.deselectAll();
        clearReportSelection();
        selectedReportCard = card;
        card.getElement().getClassList().set("selected", true);
    }

    protected void clearReportSelection() {
        if (selectedReportCard != null) {
            selectedReportCard.getElement().getClassList().set("selected", false);
            selectedReportCard = null;
        }
    }

    protected Div createCard(final String color, final String icon, final String title) {
        Div card = new Div();
        card.addClassName("report-group-card");
        card.getStyle().set("--report-group-accent", color);

        Div iconBox = new Div();
        iconBox.addClassName("report-group-card-icon");
        iconBox.add(VaadinIcon.valueOf(icon).create());

        Span titleSpan = new Span(title);
        titleSpan.addClassName("report-group-card-title");

        card.add(iconBox, titleSpan);
        // Карточка кликабельна, значит должна брать фокус.
        card.getElement().setAttribute("tabindex", "0");
        card.getElement().setAttribute("role", "button");
        return card;
    }

    /**
     * Ведение групп — только у владельца отчётов и только на верхнем уровне: внутри группы
     * показывается её содержимое, а не список для правки, а обычный сотрудник ходит по каталогу
     * и группы не правит.
     */
    protected void updateActionsVisible() {
        boolean root = path.isEmpty() && isManagePermitted();
        for (String actionId : List.of("create", "edit", "remove")) {
            Action action = reportGroupsDataGrid.getAction(actionId);
            if (action != null) {
                action.setVisible(root);
            }
        }
    }

    /**
     * Открытая сейчас группа или {@code null} на верхнем уровне.
     */
    @Nullable
    protected ExtReportGroup currentGroup() {
        return path.isEmpty() ? null : path.get(path.size() - 1);
    }

    /**
     * Проваливается внутрь группы: путь удлиняется, список перечитывается, выделение снимается —
     * оно относилось к предыдущему уровню.
     */
    protected void openGroup(final ExtReportGroup group) {
        path.add(group);
        reportGroupsDataGrid.deselectAll();
        refreshGrid();
    }

    /**
     * Поднимает навигацию на уровень {@code level}: 0 — верхний, дальше по «хлебным крошкам».
     */
    protected void openLevel(final int level) {
        while (path.size() > level) {
            path.remove(path.size() - 1);
        }
        reportGroupsDataGrid.deselectAll();
        refreshGrid();
    }

    /**
     * «Хлебные крошки» над списком: все группы → … → текущая. Последний элемент не кликабелен.
     */
    protected void renderBreadcrumb() {
        breadcrumbBox.removeAll();
        breadcrumbBox.setVisible(!path.isEmpty());
        if (path.isEmpty()) {
            return;
        }

        addCrumb(messageBundle.getMessage("breadcrumb.root"), 0, false);
        for (int i = 0; i < path.size(); i++) {
            Span separator = new Span("/");
            separator.addClassName("report-group-crumb-separator");
            breadcrumbBox.add(separator);
            addCrumb(metadataTools.getInstanceName(path.get(i)), i + 1, i == path.size() - 1);
        }
    }

    protected void addCrumb(final String text, final int level, final boolean last) {
        Span crumb = new Span(text);
        crumb.addClassName(last ? "report-group-crumb-current" : "report-group-crumb");
        if (!last) {
            crumb.addClickListener(event -> openLevel(level));
        }
        breadcrumbBox.add(crumb);
    }

    protected List<Report> reportsOf(final ExtReportGroup group) {
        return dataManager.load(Report.class)
                .query("select e from report_Report e where e.group = :group order by e.name")
                .parameter("group", group)
                .list();
    }

    /**
     * Подсвечивает карточку выбранной в гриде группы и гасит остальные.
     */
    protected void highlightSelected() {
        ReportGroup selected = reportGroupsDataGrid.getSingleSelectedItem();
        if (selected != null) {
            clearReportSelection();
        }
        cardsByGroup.forEach((group, card) ->
                card.getElement().getClassList().set("selected", group.equals(selected)));
    }

    /**
     * Новая группа заводится внутри открытой: пользователь создаёт её там, где стоит, а не
     * всегда на верхнем уровне.
     */
    @Install(to = "reportGroupsDataGrid.create", subject = "initializer")
    protected void reportGroupsDataGridCreateInitializer(final ReportGroup group) {
        ((ExtReportGroup) group).setParent(currentGroup());
    }

    /**
     * Группы, объявленные аннотацией в коде, править и удалять нельзя — редактируются только те,
     * что заведены в базе.
     */
    @Install(to = "reportGroupsDataGrid.remove", subject = "enabledRule")
    protected boolean reportGroupsDataGridRemoveEnabledRule() {
        ReportGroup group = reportGroupsDataGrid.getSingleSelectedItem();
        // Нельзя удалять только группы из аннотаций — их нет в базе. Источник сравнивается
        // именно с ним: у группы, собранной не загрузкой из базы, он может быть не проставлен,
        // и строгая проверка на DATABASE гасила кнопку на вполне удаляемой группе.
        return group != null && group.getSource() != ReportSource.ANNOTATED_CLASS;
    }

    @Subscribe("reportGroupsDataGrid")
    public void onReportGroupsDataGridSelection(final SelectionEvent<DataGrid<ReportGroup>, ReportGroup> event) {
        highlightSelected();

        ReportGroup group = reportGroupsDataGrid.getSingleSelectedItem();
        String text;
        if (group == null || group.getSource() == ReportSource.DATABASE) {
            text = messages.getMessage("actions.Edit");
        } else {
            text = messages.getMessage("actions.Read");
        }
        editAction.setText(text);
    }

    @Subscribe("reportGroupsDataGrid.remove")
    public void onReportGroupsDataGridRemove(final ActionPerformedEvent event) {
        if (!event.getSource().isEnabled()) {
            return;
        }
        removeSelectedGroup();
    }

    /**
     * Удаление выбранной группы с теми же проверками, что и у кнопки на панели.
     */
    protected void removeSelectedGroup() {
        ReportGroup group = reportGroupsDataGrid.getSingleSelectedItem();
        if (group == null || group.getSource() == ReportSource.ANNOTATED_CLASS) {
            return;
        }

        // Аддон считает системной любую группу с кодом, а у нас код заполняют почти всегда —
        // такая проверка запрещала бы удалять что угодно. Защищаем только группу по умолчанию.
        if (DEFAULT_GROUP_CODE.equals(group.getCode())) {
            notifications.create(messageBundle.getMessage("unableToDeleteSystemReportGroup"))
                    .withType(Notifications.Type.WARNING)
                    .show();
        } else if (reportRepository.existsReportByGroup(group)) {
            notifications.create(messageBundle.getMessage("unableToDeleteNotEmptyReportGroup"))
                    .withType(Notifications.Type.WARNING)
                    .show();
        } else {
            removeAction.execute();
        }
    }

    @Install(to = "reportGroupsDataGrid.edit", subject = "viewConfigurer")
    private void reportGroupsDataGridEditViewConfigurer(final ReportGroupDetailView reportGroupDetailView) {
        ReportGroup selectedItem = reportGroupsDataGrid.getSingleSelectedItem();
        if (selectedItem == null) {
            return;
        }
        reportGroupDetailView.setReadOnly(selectedItem.getSource() == ReportSource.ANNOTATED_CLASS);
    }

    /**
     * Группы приходят не из JPA, а из репозитория аддона — он же склеивает записи базы с теми,
     * что объявлены аннотациями, поэтому загрузка и подсчёт идут через делегаты.
     */
    @Install(to = "groupsDl", target = Target.DATA_LOADER)
    public List<ReportGroup> groupsDlLoadDelegate(final LoadContext<ReportGroup> loadContext) {
        ReportGroupLoadContext context = new ReportGroupLoadContext(
                createFilter(),
                sortOrder(),
                loadContext.getQuery().getFirstResult(),
                loadContext.getQuery().getMaxResults()
        );

        // Репозиторий отдаёт все группы разом — и из базы, и объявленные аннотациями, — а
        // фильтровать по родителю он не умеет, поэтому уровень отбирается здесь. Групп немного,
        // на список это не влияет.
        UUID parentId = currentGroup() == null ? null : currentGroup().getId();
        Map<UUID, UUID> parents = parentIds();
        return reportGroupRepository.loadList(context).stream()
                .filter(group -> Objects.equals(parents.get(group.getId()), parentId))
                .toList();
    }

    /**
     * Родители групп: {@code id → id родителя}.
     * <p>
     * Читается отдельным запросом, потому что репозиторий аддона грузит группы с
     * {@code FetchPlan.BASE} — ссылок в нём нет, и обращение к {@code getParent()} у такой
     * группы упало бы на незагруженном атрибуте. Групп, объявленных аннотациями, в выборке нет:
     * они не лежат в базе и всегда остаются на верхнем уровне.
     */
    protected Map<UUID, UUID> parentIds() {
        Map<UUID, UUID> result = new LinkedHashMap<>();
        // left join обязателен: через e.parent.id получился бы inner join и группы верхнего
        // уровня (у которых родителя нет) выпали бы из выборки.
        dataManager.loadValues("select e.id, p.id from umida_ExtReportGroup e left join e.parent p")
                .properties("id", "parentId")
                .list()
                .forEach(row -> result.put(row.getValue("id"), row.getValue("parentId")));
        return result;
    }

    /**
     * Панели фильтров на экране нет — показываются все группы, поэтому фильтр репозитория
     * остаётся пустым.
     */
    protected ReportGroupFilter createFilter() {
        return new ReportGroupFilter();
    }

    protected void refreshGrid() {
        groupsDl.load();
    }

    @Supply(to = "reportGroupsDataGrid.title", subject = "renderer")
    protected Renderer<ReportGroup> reportGroupsDataGridTitleRenderer() {
        return new TextRenderer<>(metadataTools::getInstanceName);
    }
}
