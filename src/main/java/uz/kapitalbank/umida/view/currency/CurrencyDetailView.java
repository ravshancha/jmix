package uz.kapitalbank.umida.view.currency;

import com.vaadin.flow.router.Route;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.entity.Currency;
import uz.kapitalbank.umida.repository.CurrencyRepository;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.Optional;
import java.util.Set;

@Route(value = "currencies/:id", layout = MainView.class)
@ViewController(id = "umida_Currency.detail")
@ViewDescriptor(path = "currency-detail-view.xml")
@EditedEntityContainer("currencyDc")
public class CurrencyDetailView extends StandardDetailView<Currency> {

    @Autowired
    private CurrencyRepository repository;

    @Install(to = "currencyDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private Optional<Currency> loadDelegate(Long id, FetchPlan fetchPlan) {
        return repository.findById(id, fetchPlan);
    }

    @Install(target = Target.DATA_CONTEXT)
    private Set<Object> saveDelegate(SaveContext saveContext) {
        return Set.of(repository.save(getEditedEntity()));
    }
}