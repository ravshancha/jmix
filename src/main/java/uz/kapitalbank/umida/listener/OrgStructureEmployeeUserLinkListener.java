package uz.kapitalbank.umida.listener;

import io.jmix.core.DataManager;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import uz.kapitalbank.umida.entity.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.User;

/**
 * Links {@link OrgStructureEmployee#getUser()} to the {@link User} with the same email, right
 * before the employee row is saved. Runs only when the employee has an email and no user linked
 * yet, so a manually picked user (or one linked earlier) is never overwritten.
 */
@Component
public class OrgStructureEmployeeUserLinkListener {

    private final DataManager dataManager;

    public OrgStructureEmployeeUserLinkListener(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @EventListener
    public void onEmployeeSaving(EntitySavingEvent<OrgStructureEmployee> event) {
        OrgStructureEmployee employee = event.getEntity();
        if (employee.getUser() != null || employee.getEmail() == null || employee.getEmail().isBlank()) {
            return;
        }

        dataManager.load(User.class)
                .query("select u from umida_User u where lower(u.email) = lower(:email)")
                .parameter("email", employee.getEmail())
                .optional()
                .ifPresent(employee::setUser);
    }
}
