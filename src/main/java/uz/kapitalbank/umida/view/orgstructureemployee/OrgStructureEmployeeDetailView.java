package uz.kapitalbank.umida.view.orgstructureemployee;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.entity.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.view.main.MainView;

@Route(value = "org-structure-employees/:id", layout = MainView.class)
@ViewController(id = "OrgStructureEmployee.detail")
@ViewDescriptor(path = "org-structure-employee-detail-view.xml")
@EditedEntityContainer("orgStructureEmployeeDc")
@DialogMode(width = "48em", height = "90%", resizable = true)
public class OrgStructureEmployeeDetailView extends StandardDetailView<OrgStructureEmployee> {

    @ViewComponent
    private TypedTextField<String> emailField;

    @ViewComponent
    private InstanceContainer<OrgStructureEmployee> orgStructureEmployeeDc;

    @Autowired
    private DataManager dataManager;

    /**
     * As soon as an email is typed in, checks for a matching {@code User} and links it right away
     * (instead of only on save, see {@code OrgStructureEmployeeUserLinkListener}), so the "User"
     * field visibly fills in before the form is submitted. Never overwrites an already-picked user.
     */
    @Subscribe("emailField")
    public void onEmailFieldComponentValueChange(
            AbstractField.ComponentValueChangeEvent<TypedTextField<String>, String> event) {
        OrgStructureEmployee employee = orgStructureEmployeeDc.getItem();
        String email = event.getValue();
        if (employee.getUser() != null || email == null || email.isBlank()) {
            return;
        }

        dataManager.load(User.class)
                .query("select u from umida_User u where lower(u.email) = lower(:email)")
                .parameter("email", email)
                .optional()
                .ifPresent(employee::setUser);
    }
}
