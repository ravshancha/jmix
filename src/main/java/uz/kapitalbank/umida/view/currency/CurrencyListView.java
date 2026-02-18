package uz.kapitalbank.umida.view.currency;

import com.vaadin.flow.router.Route;
import io.jmix.core.repository.JmixDataRepositoryContext;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import uz.kapitalbank.umida.entity.Currency;
import uz.kapitalbank.umida.repository.CurrencyRepository;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.Collection;
import java.util.List;

@Route(value = "currencies", layout = MainView.class)
@ViewController(id = "umida_Currency.list")
@ViewDescriptor(path = "currency-list-view.xml")
@LookupComponent("currenciesDataGrid")
@DialogMode(width = "64em")
public class CurrencyListView extends StandardListView<Currency> {

    @Autowired
    private CurrencyRepository repository;

    @Install(to = "currenciesDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private List<Currency> loadDelegate(Pageable pageable, JmixDataRepositoryContext context) {
        return repository.findAllSlice(pageable, context).getContent();
    }

    @Install(to = "currenciesDataGrid.removeAction", subject = "delegate")
    private void currenciesDataGridRemoveDelegate(final Collection<Currency> collection) {
        repository.deleteAll(collection);
    }

    @Install(to = "pagination", subject = "totalCountByRepositoryDelegate")
    private Long paginationTotalCountByRepositoryDelegate(final JmixDataRepositoryContext context) {
        return repository.count(context);
    }
}