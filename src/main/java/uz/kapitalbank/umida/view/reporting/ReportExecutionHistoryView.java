package uz.kapitalbank.umida.view.reporting;

import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.AccessManager;
import io.jmix.core.DataLoadContext;
import io.jmix.core.DataManager;
import io.jmix.core.LoadContext;
import io.jmix.core.accesscontext.SpecificOperationAccessContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.Install;
import io.jmix.flowui.view.MessageBundle;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.Supply;
import io.jmix.flowui.view.Target;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.jmix.reports.entity.Report;
import io.jmix.reports.entity.ReportExecution;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.List;

/**
 * Журнал запусков одного отчёта: экран всегда открывается по выбранной в «Отчётности» строке и
 * показывает только её запуски. Владелец видит запуски всех сотрудников (с колонкой «Пользователь»),
 * обычный сотрудник — только свои.
 */
@Route(value = "report-execution-history", layout = MainView.class)
@ViewController(id = "ReportExecution.history")
@ViewDescriptor(path = "report-execution-history-view.xml")
@DialogMode(width = "75em")
public class ReportExecutionHistoryView extends StandardListView<ReportExecution> {

    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private AccessManager accessManager;
    @Autowired
    private Downloader downloader;
    @Autowired
    private Notifications notifications;

    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private DataGrid<ReportExecution> executionsDataGrid;

    /**
     * Отчёт, в разрезе которого смотрят журнал. Ставится вызывающим экраном до открытия окна,
     * поэтому первый же запрос загрузчика уже ограничен им.
     */
    private Report report;

    /**
     * Ограничивает журнал одним отчётом и выносит его название в заголовок окна.
     */
    public void setReport(Report report) {
        this.report = report;
        setPageTitle(messageBundle.formatMessage("titleForReport", report.getName()));
    }

    /**
     * Hides the user column for non-managers: regular users only ever see their
     * own runs, so the column would be redundant.
     */
    @Subscribe
    public void onInit(InitEvent event) {
        if (!isManagePermitted()) {
            executionsDataGrid.getColumnByKey("username").setVisible(false);
        }
        // Отчёт во всех строках один и тот же — он в заголовке окна, колонка была бы повтором.
        executionsDataGrid.getColumnByKey("reportName").setVisible(false);
    }

    @Supply(to = "executionsDataGrid.statusIcon", subject = "renderer")
    private Renderer<ReportExecution> statusIconRenderer() {
        return new ComponentRenderer<>(execution -> {
            Icon icon;
            if (Boolean.TRUE.equals(execution.getSuccess())) {
                icon = VaadinIcon.CHECK_CIRCLE.create();
                icon.setColor("var(--lumo-success-color)");
            } else if (Boolean.TRUE.equals(execution.getCancelled())) {
                icon = VaadinIcon.BAN.create();
                icon.setColor("var(--lumo-contrast-50pct)");
            } else {
                icon = VaadinIcon.CLOSE_CIRCLE.create();
                icon.setColor("var(--lumo-error-color)");
            }
            icon.setSize("var(--lumo-icon-size-m)");
            return icon;
        });
    }

    @Subscribe("executionsDataGrid.download")
    public void onDownload(ActionPerformedEvent event) {
        ReportExecution execution = executionsDataGrid.getSingleSelectedItem();
        if (execution == null) {
            return;
        }
        if (execution.getOutputDocument() == null) {
            notifications.create(messageBundle.getMessage("noOutputDocument"))
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }
        downloader.download(execution.getOutputDocument());
    }

    @Install(to = "executionsDl", target = Target.DATA_LOADER)
    private List<ReportExecution> executionsDlLoadDelegate(LoadContext<ReportExecution> loadContext) {
        if (report == null) {
            // Экран открыт по прямой ссылке, без выбранного отчёта: показывать нечего.
            return List.of();
        }
        var loader = dataManager.load(ReportExecution.class)
                .query(buildQuery())
                .fetchPlan(loadContext.getFetchPlan())
                .firstResult(loadContext.getQuery().getFirstResult())
                .maxResults(loadContext.getQuery().getMaxResults());
        applyParameters(loader::parameter);
        return loader.list();
    }

    @Install(to = "pagination", subject = "totalCountDelegate")
    private Integer paginationTotalCountDelegate(DataLoadContext ignored) {
        if (report == null) {
            return 0;
        }
        var loader = dataManager.loadValue(
                "select count(e) " + buildQueryBody(), Long.class);
        applyParameters(loader::parameter);
        return loader.one().intValue();
    }

    private String buildQuery() {
        return "select e " + buildQueryBody() + " order by e.startTime desc";
    }

    /**
     * Отчёт в условии всегда — журнал смотрят в разрезе одного отчёта, независимо от роли.
     * Обычному сотруднику к нему добавляются только его собственные запуски.
     */
    private String buildQueryBody() {
        StringBuilder sb = new StringBuilder("from report_ReportExecution e where e.report.id = :reportId");
        if (!isManagePermitted()) {
            sb.append(" and e.username = :username");
        }
        return sb.toString();
    }

    private void applyParameters(java.util.function.BiConsumer<String, Object> parameterSetter) {
        parameterSetter.accept("reportId", report.getId());
        if (!isManagePermitted()) {
            parameterSetter.accept("username", currentAuthentication.getUser().getUsername());
        }
    }

    private boolean isManagePermitted() {
        SpecificOperationAccessContext context =
                new SpecificOperationAccessContext(ReportingMainView.MANAGE_USER_REPORTS);
        accessManager.applyRegisteredConstraints(context);
        return context.isPermitted();
    }
}
