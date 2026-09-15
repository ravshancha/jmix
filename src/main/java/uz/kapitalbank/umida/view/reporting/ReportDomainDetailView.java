package uz.kapitalbank.umida.view.reporting;

import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.entity.ReportDomain;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.*;

@Route(value = "report-domains/:id", layout = MainView.class)
@ViewController(id = "umida_ReportDomain.detail")
@ViewDescriptor(path = "report-domain-detail-view.xml")
@EditedEntityContainer("reportDomainDc")
@DialogMode(width = "48em", resizable = true)
public class ReportDomainDetailView extends StandardDetailView<ReportDomain> {

    /**
     * Языки названий области — те же локали, что и у приложения.
     */
    private static final List<String> LANGUAGES = List.of("uz", "ru", "en");

    @ViewComponent
    private JmixSelect<String> languageField;
    @ViewComponent
    private MessageBundle messageBundle;

    @Autowired
    private DataManager dataManager;

    @Subscribe
    public void onInit(final InitEvent event) {
        languageField.setItems(LANGUAGES);
    }

    /**
     * Родитель не должен приводить к кольцу: область, выбранная родителем, не может лежать внутри
     * редактируемой — иначе дерево справочника не построится, а сама ветка пропадёт из списка.
     */
    @Subscribe
    public void onValidation(final ValidationEvent event) {
        ReportDomain parent = getEditedEntity().getParent();
        if (parent == null) {
            return;
        }
        if (Objects.equals(parent.getId(), getEditedEntity().getId())
                || isDescendant(parent.getId(), getEditedEntity().getId())) {
            event.getErrors().add(messageBundle.getMessage("parentCycle"));
        }
    }

    /**
     * Лежит ли {@code candidateId} внутри {@code ancestorId}. Родители читаются отдельным
     * запросом: у области, пришедшей из поля выбора, загружено только название, и обращение к её
     * {@code parent} упало бы на незагруженном атрибуте.
     */
    private boolean isDescendant(final UUID candidateId, final UUID ancestorId) {
        Map<UUID, UUID> parents = new LinkedHashMap<>();
        dataManager.loadValues("select e.id, p.id from umida_ReportDomain e left join e.parent p")
                .properties("id", "parentId")
                .list()
                .forEach(row -> parents.put(row.getValue("id"), row.getValue("parentId")));

        UUID current = parents.get(candidateId);
        // Ограничение по числу областей: даже если в базе уже есть кольцо, обход не зациклится.
        for (int step = 0; current != null && step <= parents.size(); step++) {
            if (Objects.equals(current, ancestorId)) {
                return true;
            }
            current = parents.get(current);
        }
        return false;
    }
}
