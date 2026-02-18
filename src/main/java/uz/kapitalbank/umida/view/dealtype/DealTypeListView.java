package uz.kapitalbank.umida.view.dealtype;

import com.vaadin.flow.router.Route;
import io.jmix.core.repository.JmixDataRepositoryContext;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import uz.kapitalbank.umida.entity.DealType;
import uz.kapitalbank.umida.repository.DealTypeRepository;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.Collection;
import java.util.List;

@Route(value = "deal-types", layout = MainView.class)
@ViewController(id = "umida_DealType.list")
@ViewDescriptor(path = "deal-type-list-view.xml")
@LookupComponent("dealTypesDataGrid")
@DialogMode(width = "64em")
public class DealTypeListView extends StandardListView<DealType> {

    @Autowired
    private DealTypeRepository repository;

    @Install(to = "dealTypesDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private List<DealType> loadDelegate(Pageable pageable, JmixDataRepositoryContext context) {
        return repository.findAllSlice(pageable, context).getContent();
    }

    @Install(to = "dealTypesDataGrid.removeAction", subject = "delegate")
    private void dealTypesDataGridRemoveDelegate(final Collection<DealType> collection) {
        repository.deleteAll(collection);
    }

    @Install(to = "pagination", subject = "totalCountByRepositoryDelegate")
    private Long paginationTotalCountByRepositoryDelegate(final JmixDataRepositoryContext context) {
        return repository.count(context);
    }
}