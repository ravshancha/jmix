package uz.kapitalbank.umida.view.division;

import com.vaadin.flow.router.Route;
import io.jmix.core.repository.JmixDataRepositoryContext;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import uz.kapitalbank.umida.entity.Division;
import uz.kapitalbank.umida.repository.DivisionRepository;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.Collection;
import java.util.List;

@Route(value = "divisions", layout = MainView.class)
@ViewController(id = "umida_Division.list")
@ViewDescriptor(path = "division-list-view.xml")
@LookupComponent("divisionsDataGrid")
@DialogMode(width = "64em")
public class DivisionListView extends StandardListView<Division> {

    @Autowired
    private DivisionRepository repository;

    @Install(to = "divisionsDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private List<Division> loadDelegate(Pageable pageable, JmixDataRepositoryContext context) {
        return repository.findAllSlice(pageable, context).getContent();
    }

    @Install(to = "divisionsDataGrid.removeAction", subject = "delegate")
    private void divisionsDataGridRemoveDelegate(final Collection<Division> collection) {
        repository.deleteAll(collection);
    }

    @Install(to = "pagination", subject = "totalCountByRepositoryDelegate")
    private Long paginationTotalCountByRepositoryDelegate(final JmixDataRepositoryContext context) {
        return repository.count(context);
    }
}