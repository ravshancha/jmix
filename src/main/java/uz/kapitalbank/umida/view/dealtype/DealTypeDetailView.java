package uz.kapitalbank.umida.view.dealtype;

import com.vaadin.flow.router.Route;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.entity.DealType;
import uz.kapitalbank.umida.repository.DealTypeRepository;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.Optional;
import java.util.Set;

@Route(value = "deal-types/:id", layout = MainView.class)
@ViewController(id = "umida_DealType.detail")
@ViewDescriptor(path = "deal-type-detail-view.xml")
@EditedEntityContainer("dealTypeDc")
public class DealTypeDetailView extends StandardDetailView<DealType> {

    @Autowired
    private DealTypeRepository repository;

    @Install(to = "dealTypeDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private Optional<DealType> loadDelegate(Long id, FetchPlan fetchPlan) {
        return repository.findById(id, fetchPlan);
    }

    @Install(target = Target.DATA_CONTEXT)
    private Set<Object> saveDelegate(SaveContext saveContext) {
        return Set.of(repository.save(getEditedEntity()));
    }
}