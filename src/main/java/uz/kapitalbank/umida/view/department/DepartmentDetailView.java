package uz.kapitalbank.umida.view.department;

import com.vaadin.flow.router.Route;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.entity.Department;
import uz.kapitalbank.umida.repository.DepartmentRepository;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.Optional;
import java.util.Set;

@Route(value = "departments/:id", layout = MainView.class)
@ViewController(id = "umida_Department.detail")
@ViewDescriptor(path = "department-detail-view.xml")
@EditedEntityContainer("departmentDc")
public class DepartmentDetailView extends StandardDetailView<Department> {

    @Autowired
    private DepartmentRepository repository;

    @Install(to = "departmentDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private Optional<Department> loadDelegate(Integer id, FetchPlan fetchPlan) {
        return repository.findById(id, fetchPlan);
    }

    @Install(target = Target.DATA_CONTEXT)
    private Set<Object> saveDelegate(SaveContext saveContext) {
        return Set.of(repository.save(getEditedEntity()));
    }
}