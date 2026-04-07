package uz.kapitalbank.umida.view.department;

import com.vaadin.flow.router.Route;
import io.jmix.core.repository.JmixDataRepositoryContext;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import uz.kapitalbank.umida.entity.Department;
import uz.kapitalbank.umida.repository.DepartmentRepository;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.Collection;
import java.util.List;

@Route(value = "departments", layout = MainView.class)
@ViewController(id = "umida_Department.list")
@ViewDescriptor(path = "department-list-view.xml")
@LookupComponent("departmentsDataGrid")
@DialogMode(width = "64em")
public class DepartmentListView extends StandardListView<Department> {

    @Autowired
    private DepartmentRepository repository;

    @Install(to = "departmentsDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private List<Department> loadDelegate(Pageable pageable, JmixDataRepositoryContext context) {
        return repository.findAllSlice(pageable, context).getContent();
    }

    @Install(to = "departmentsDataGrid.removeAction", subject = "delegate")
    private void departmentsDataGridRemoveDelegate(final Collection<Department> collection) {
        repository.deleteAll(collection);
    }

    @Install(to = "pagination", subject = "totalCountByRepositoryDelegate")
    private Long paginationTotalCountByRepositoryDelegate(final JmixDataRepositoryContext context) {
        return repository.count(context);
    }
}