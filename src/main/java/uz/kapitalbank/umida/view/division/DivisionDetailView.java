package uz.kapitalbank.umida.view.division;

import com.vaadin.flow.router.Route;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.entity.Division;
import uz.kapitalbank.umida.repository.DivisionRepository;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.Optional;
import java.util.Set;

@Route(value = "divisions/:id", layout = MainView.class)
@ViewController(id = "umida_Division.detail")
@ViewDescriptor(path = "division-detail-view.xml")
@EditedEntityContainer("divisionDc")
public class DivisionDetailView extends StandardDetailView<Division> {

    @Autowired
    private DivisionRepository repository;

    @Install(to = "divisionDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private Optional<Division> loadDelegate(Long id, FetchPlan fetchPlan) {
        return repository.findById(id, fetchPlan);
    }

    @Install(target = Target.DATA_CONTEXT)
    private Set<Object> saveDelegate(SaveContext saveContext) {
        return Set.of(repository.save(getEditedEntity()));
    }
}