package uz.kapitalbank.umida.listener;

import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.service.UserReportSyncService;

/**
 * Fills Reporting from the reports table when the application starts.
 * <p>
 * {@code ReportChangedListener} covers reports created while the application runs, but reports can
 * also appear with it down — a restored dump, a changelog, a direct insert — and reports that
 * predate Reporting have no row either. Running the sync once on startup means the list is
 * complete before the first user opens it.
 */
@Component
public class ReportingStartupSync {

    private static final Logger log = LoggerFactory.getLogger(ReportingStartupSync.class);

    /**
     * Owner given to reports whose creator is not (or no longer) a user of this application. There
     * is no session at startup, so the administrator account stands in for them.
     */
    private static final String FALLBACK_OWNER_USERNAME = "admin";

    private final UserReportSyncService userReportSyncService;
    private final DataManager dataManager;
    private final SystemAuthenticator systemAuthenticator;

    public ReportingStartupSync(UserReportSyncService userReportSyncService,
                                   DataManager dataManager,
                                   SystemAuthenticator systemAuthenticator) {
        this.userReportSyncService = userReportSyncService;
        this.dataManager = dataManager;
        this.systemAuthenticator = systemAuthenticator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncReporting() {
        try {
            User fallbackOwner = fallbackOwner();
            int linked = userReportSyncService.syncMissingReports(fallbackOwner);
            if (linked > 0) {
                log.info("Reporting: linked {} report(s) that had no row yet", linked);
            }
        } catch (Exception e) {
            // A failed sync must not keep the application from starting: Reporting runs the
            // same sync when it is opened, so the rows are picked up there.
            log.error("Reporting: startup sync failed", e);
        }
    }

    @Nullable
    private User fallbackOwner() {
        return systemAuthenticator.withSystem(() -> dataManager.load(User.class)
                .query("select u from umida_User u where u.username = :username")
                .parameter("username", FALLBACK_OWNER_USERNAME)
                .optional()
                .orElse(null));
    }
}
