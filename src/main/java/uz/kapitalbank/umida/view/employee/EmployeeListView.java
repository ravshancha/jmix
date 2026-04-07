package uz.kapitalbank.umida.view.employee;

import com.vaadin.flow.router.Route;
import io.jmix.core.repository.JmixDataRepositoryContext;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import uz.kapitalbank.umida.entity.Employee;
import uz.kapitalbank.umida.repository.EmployeeRepository;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.Collection;
import java.util.List;

@Route(value = "employees", layout = MainView.class)
@ViewController(id = "umida_Employee.list")
@ViewDescriptor(path = "employee-list-view.xml")
@LookupComponent("employeesDataGrid")
@DialogMode(width = "64em")
public class EmployeeListView extends StandardListView<Employee> {

    @Autowired
    private EmployeeRepository repository;

    @Install(to = "employeesDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private List<Employee> loadDelegate(Pageable pageable, JmixDataRepositoryContext context) {
        return repository.findAllSlice(pageable, context).getContent();
    }

    @Install(to = "employeesDataGrid.removeAction", subject = "delegate")
    private void employeesDataGridRemoveDelegate(final Collection<Employee> collection) {
        repository.deleteAll(collection);
    }

    @Install(to = "pagination", subject = "totalCountByRepositoryDelegate")
    private Long paginationTotalCountByRepositoryDelegate(final JmixDataRepositoryContext context) {
        return repository.count(context);
    }
}