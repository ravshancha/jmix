package uz.kapitalbank.umida.view.reporting;

import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.kit.action.Action;
import io.jmix.flowui.view.*;
import uz.kapitalbank.umida.entity.ReportDomain;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.List;

/**
 * Справочник предметных областей отчётов. Тот же экран открывается диалогом из поля «Домен»
 * в редакторе отчёта, поэтому список — дерево: поддомен виден внутри своей области и выбирается
 * так же, как область верхнего уровня.
 */
@Route(value = "report-domains", layout = MainView.class)
@ViewController(id = "umida_ReportDomain.list")
@ViewDescriptor(path = "report-domain-list-view.xml")
@LookupComponent("reportDomainsTreeDataGrid")
@DialogMode(width = "64em", height = "40em", resizable = true)
public class ReportDomainListView extends StandardListView<ReportDomain> {

    @ViewComponent
    private TreeDataGrid<ReportDomain> reportDomainsTreeDataGrid;
    @ViewComponent
    private HorizontalLayout buttonsPanel;

    /**
     * Экран открыт из поля «Домен» редактора отчёта — тогда справочник только для выбора:
     * ведение областей остаётся в его собственном пункте меню, из карточки отчёта справочник не
     * правят.
     */
    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        if (getSelectionHandler().isEmpty()) {
            return;
        }
        buttonsPanel.setVisible(false);
        for (String actionId : List.of("createAction", "editAction", "removeAction")) {
            Action action = reportDomainsTreeDataGrid.getAction(actionId);
            if (action != null) {
                action.setVisible(false);
            }
        }
    }

    /**
     * Новая область заводится внутри выделенной: обычно в справочник добавляют поддомен, а не
     * ещё одну область верхнего уровня. Родителя всегда можно очистить в самом редакторе.
     */
    @Install(to = "reportDomainsTreeDataGrid.createAction", subject = "initializer")
    protected void reportDomainsTreeDataGridCreateInitializer(final ReportDomain domain) {
        domain.setParent(reportDomainsTreeDataGrid.getSingleSelectedItem());
    }
}
