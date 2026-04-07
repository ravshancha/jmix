package uz.kapitalbank.umida.view.employee;

import com.vaadin.flow.router.Route;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.entity.Employee;
import uz.kapitalbank.umida.repository.EmployeeRepository;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.Optional;
import java.util.Set;

@Route(value = "employees/:id", layout = MainView.class)
@ViewController(id = "umida_Employee.detail")
@ViewDescriptor(path = "employee-detail-view.xml")
@EditedEntityContainer("employeeDc")
public class EmployeeDetailView extends StandardDetailView<Employee> {

    @Autowired
    private EmployeeRepository repository;

    @Install(to = "employeeDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private Optional<Employee> loadDelegate(Long id, FetchPlan fetchPlan) {
        return repository.findById(id, fetchPlan);
    }

    @Install(target = Target.DATA_CONTEXT)
    private Set<Object> saveDelegate(SaveContext saveContext) {
        return Set.of(repository.save(getEditedEntity()));
    }
}