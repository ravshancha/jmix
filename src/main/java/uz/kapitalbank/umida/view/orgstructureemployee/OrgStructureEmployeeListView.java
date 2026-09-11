package uz.kapitalbank.umida.view.orgstructureemployee;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.action.list.ReadAction;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.Action;
import io.jmix.flowui.view.*;
import uz.kapitalbank.umida.entity.OrgStructureEmployee;
import uz.kapitalbank.umida.view.main.MainView;

@Route(value = "org-structure-employees", layout = MainView.class)
@ViewController(id = "OrgStructureEmployee.list")
@ViewDescriptor(path = "org-structure-employee-list-view.xml")
@LookupComponent("orgStructureEmployeesDataGrid")
@DialogMode(width = "64em")
public class OrgStructureEmployeeListView extends StandardListView<OrgStructureEmployee> {

    @ViewComponent
    private DataGrid<OrgStructureEmployee> orgStructureEmployeesDataGrid;

    @Subscribe
    public void onInit(InitEvent event) {
        Action readAction = orgStructureEmployeesDataGrid.getAction("readAction");
        if (readAction instanceof ReadAction<?> read) {
            read.setOpenMode(OpenMode.DIALOG);
        }
    }
}
