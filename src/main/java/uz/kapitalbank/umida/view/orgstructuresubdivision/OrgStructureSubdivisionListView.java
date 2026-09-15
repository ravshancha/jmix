package uz.kapitalbank.umida.view.orgstructuresubdivision;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.lang.Nullable;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.view.main.MainView;

/**
 * Справочник подразделений оргструктуры. Данные читаются из витрины DWH, поэтому экран только
 * для просмотра и выбора: ни создания, ни правки, ни удаления здесь нет.
 * <p>
 * Тот же экран открывается диалогом из поля «Владелец» в карточке отчёта — кнопки «Выбрать» и
 * «Отмена» показывает сам {@link StandardListView}, когда экран открыт на выбор.
 */
@Route(value = "org-structure-subdivisions", layout = MainView.class)
@ViewController(id = "umida_OrgStructureSubdivision.list")
@ViewDescriptor(path = "org-structure-subdivision-list-view.xml")
@LookupComponent("subdivisionsDataGrid")
@DialogMode(width = "56em", height = "40em", resizable = true)
public class OrgStructureSubdivisionListView extends StandardListView<OrgStructureSubdivision> {

    @ViewComponent
    private TypedTextField<String> nameFilterField;
    @ViewComponent
    private CollectionLoader<OrgStructureSubdivision> subdivisionsDl;

    @Subscribe
    public void onInit(final InitEvent event) {
        nameFilterField.addValueChangeListener(event1 -> reload());
    }

    /**
     * Перечитывает список с первой страницы: поиск сужает выборку, и страница, на которой был
     * пользователь, может в ней уже не существовать. Пустая строка снимает параметр — вместе с
     * ним отключается и условие запроса.
     */
    private void reload() {
        String name = trimToNull(nameFilterField.getValue());
        if (name == null) {
            subdivisionsDl.removeParameter("nameFilter");
        } else {
            subdivisionsDl.setParameter("nameFilter", "%" + name.toLowerCase() + "%");
        }
        subdivisionsDl.setFirstResult(0);
        subdivisionsDl.load();
    }

    @Nullable
    private static String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
