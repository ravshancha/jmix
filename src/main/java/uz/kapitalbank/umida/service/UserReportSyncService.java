package uz.kapitalbank.umida.service;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.reports.entity.Report;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.kapitalbank.umida.entity.ReportDomain;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.UserReport;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Keeps the Reporting list in step with the reports themselves.
 * <p>
 * Reporting shows {@link UserReport} rows, but reports are also created outside it — in the
 * add-on's own Reports view, by the report wizard or by an import. Those reports get no
 * {@code UserReport} row and would stay invisible in Reporting, so this service links every
 * report that has no row yet: {@link #linkReport(UUID, User)} on save, driven by
 * {@code ReportChangedListener}, and
 * {@link #syncMissingReports(User)} as a catch-up for reports written around that listener (a
 * direct SQL insert, a restored dump).
 * <p>
 * Reporting is a shared list (its loader ignores the owner), therefore a report is linked
 * once globally rather than once per user; the owner column then names whoever created the report.
 * Persistence runs in a system context because regular users only have READ permission on
 * {@code UserReport}.
 */
@Service
public class UserReportSyncService {

    private static final Logger log = LoggerFactory.getLogger(UserReportSyncService.class);

    private final DataManager dataManager;
    private final SystemAuthenticator systemAuthenticator;

    public UserReportSyncService(DataManager dataManager, SystemAuthenticator systemAuthenticator) {
        this.dataManager = dataManager;
        this.systemAuthenticator = systemAuthenticator;
    }

    /**
     * Links every report that is not in Reporting yet.
     *
     * @param fallbackOwner owner used for reports whose creator is unknown or no longer a user;
     *                      when it is {@code null} such reports are skipped, since the owner column
     *                      is mandatory
     * @return the number of reports added to Reporting
     */
    @Transactional
    public int syncMissingReports(@Nullable User fallbackOwner) {
        return systemAuthenticator.withSystem(() -> {
            Set<UUID> linkedReportIds = dataManager.loadValues(
                            "select e.report.id from UserReport e")
                    .properties("reportId")
                    .list().stream()
                    .map(row -> (UUID) row.getValue("reportId"))
                    .collect(Collectors.toSet());

            // Only the id and the creator are needed, so the report rows (which carry the report
            // definition as a large XML column) are not loaded as entities.
            Map<UUID, String> creatorsByReportId = new LinkedHashMap<>();
            dataManager.loadValues("select e.id, e.createdBy from report_Report e")
                    .properties("reportId", "createdBy")
                    .list()
                    .forEach(row -> creatorsByReportId.put(
                            (UUID) row.getValue("reportId"), row.getValue("createdBy")));

            Map<String, User> ownersByUsername = new HashMap<>();
            List<Object> created = new ArrayList<>();
            creatorsByReportId.forEach((reportId, createdBy) -> {
                if (linkedReportIds.contains(reportId)) {
                    return;
                }
                User owner = resolveOwner(createdBy, fallbackOwner, ownersByUsername);
                if (owner == null) {
                    log.warn("Report {} has no owner to link it to (created by '{}'), skipped", reportId, createdBy);
                    return;
                }
                UserReport userReport = dataManager.create(UserReport.class);
                userReport.setOwner(owner);
                userReport.setReport(dataManager.getReference(Report.class, reportId));
                created.add(userReport);
            });

            if (!created.isEmpty()) {
                dataManager.save(created.toArray());
            }
            return created.size();
        });
    }

    /**
     * Links a single report, doing nothing if it already has a Reporting row.
     * <p>
     * Called from {@code ReportChangedListener} right after a report
     * is saved, so Reporting picks it up without waiting for the next
     * {@link #syncMissingReports(User)}.
     *
     * @param fallbackOwner owner used when the creator is unknown or no longer a user; when it is
     *                      {@code null} and the creator cannot be resolved, the report is left for
     *                      {@link #syncMissingReports(User)} to link
     * @return true if a row was created
     */
    // REQUIRES_NEW because the caller is an after-commit listener: the report's transaction is
    // already finished there, and joining it would leave the row uncommitted.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean linkReport(UUID reportId, @Nullable User fallbackOwner) {
        return systemAuthenticator.withSystem(() -> {
            boolean alreadyLinked = !dataManager.loadValues(
                            "select e.id from UserReport e where e.report.id = :reportId")
                    .parameter("reportId", reportId)
                    .properties("id")
                    .maxResults(1)
                    .list().isEmpty();
            if (alreadyLinked) {
                return false;
            }

            String createdBy = dataManager.loadValues(
                            "select e.createdBy from report_Report e where e.id = :reportId")
                    .parameter("reportId", reportId)
                    .properties("createdBy")
                    .list().stream()
                    .findFirst()
                    .map(row -> (String) row.getValue("createdBy"))
                    .orElse(null);

            User owner = resolveOwner(createdBy, fallbackOwner, new HashMap<>());
            if (owner == null) {
                return false;
            }

            UserReport userReport = dataManager.create(UserReport.class);
            userReport.setOwner(owner);
            userReport.setReport(dataManager.getReference(Report.class, reportId));
            dataManager.save(userReport);
            return true;
        });
    }

    /**
     * Предметная область отчёта, взятая со строки «Отчётности», или {@code null}, если строки ещё
     * нет либо домен не заполнен.
     */
    @Nullable
    public ReportDomain domainOf(UUID reportId) {
        return findByReport(reportId)
                .map(UserReport::getDomain)
                .orElse(null);
    }

    /**
     * Идентификатор области или {@code null}. Области сравниваются по нему, а не через
     * {@code equals}: экземпляры, пришедшие из разных загрузок, — разные объекты.
     */
    @Nullable
    private static UUID domainId(@Nullable ReportDomain domain) {
        return domain == null ? null : domain.getId();
    }

    /**
     * Проставляет отчёту домен.
     * <p>
     * Строку «Отчётности» может ещё не существовать: отчёт сохраняется раньше, чем список успевает
     * его подхватить, поэтому она сначала создаётся тем же {@link #linkReport(UUID, User)}.
     * Вызов внутренний, то есть выполняется в транзакции этого метода, а не в отдельной — так
     * созданная строка видна следующему запросу.
     *
     * @param fallbackOwner владелец для строки, если её приходится создавать
     * @return true, если домен сохранён; false, если строку создать не удалось (некому назначить
     * владельца)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean assignDomain(UUID reportId, @Nullable ReportDomain domain, @Nullable User fallbackOwner) {
        linkReport(reportId, fallbackOwner);

        return systemAuthenticator.withSystem(() -> findByReport(reportId)
                .map(userReport -> {
                    if (Objects.equals(domainId(userReport.getDomain()), domainId(domain))) {
                        return true;
                    }
                    userReport.setDomain(domain);
                    dataManager.save(userReport);
                    return true;
                })
                .orElseGet(() -> {
                    log.warn("Report {} has no Reporting row, domain not saved", reportId);
                    return false;
                }));
    }

    /**
     * Подразделение-владелец отчёта со строки «Отчётности» или {@code null}, если строки ещё нет
     * либо подразделение не заполнено.
     */
    @Nullable
    public OrgStructureSubdivision subdivisionOf(UUID reportId) {
        return findByReport(reportId)
                .map(UserReport::getSubdivision)
                .orElse(null);
    }

    /**
     * Проставляет отчёту подразделение-владельца — так же, как {@link #assignDomain}: строку
     * «Отчётности» при необходимости заводит {@link #linkReport(UUID, User)}.
     * <p>
     * Сравниваются идентификаторы, а не объекты: подразделение приходит из витрины отдельной
     * загрузкой, поэтому экземпляры разные даже для одной и той же строки.
     *
     * @param fallbackOwner владелец для строки, если её приходится создавать
     * @return true, если подразделение сохранено; false, если строку создать не удалось
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean assignSubdivision(UUID reportId, @Nullable OrgStructureSubdivision subdivision,
                                     @Nullable User fallbackOwner) {
        linkReport(reportId, fallbackOwner);

        return systemAuthenticator.withSystem(() -> findByReport(reportId)
                .map(userReport -> {
                    if (Objects.equals(subdivisionId(userReport.getSubdivision()), subdivisionId(subdivision))) {
                        return true;
                    }
                    userReport.setSubdivision(subdivision);
                    dataManager.save(userReport);
                    return true;
                })
                .orElseGet(() -> {
                    log.warn("Report {} has no Reporting row, subdivision not saved", reportId);
                    return false;
                }));
    }

    /**
     * Идентификатор подразделения или {@code null} — по той же причине, что и у области:
     * экземпляры из разных загрузок не равны друг другу.
     */
    @Nullable
    private static String subdivisionId(@Nullable OrgStructureSubdivision subdivision) {
        return subdivision == null ? null : subdivision.getId();
    }

    private Optional<UserReport> findByReport(UUID reportId) {
        return dataManager.load(UserReport.class)
                .query("select e from UserReport e where e.report.id = :reportId")
                // Домен — ссылка, а не поле строки: без него в плане обращение к getDomain()
                // упало бы на незагруженном атрибуте.
                .fetchPlan(fetchPlan -> fetchPlan.addFetchPlan(FetchPlan.BASE)
                        .add("domain", FetchPlan.INSTANCE_NAME)
                        .add("subdivision", FetchPlan.INSTANCE_NAME))
                .parameter("reportId", reportId)
                .maxResults(1)
                .list().stream()
                .findFirst();
    }

    @Nullable
    private User resolveOwner(@Nullable String username, @Nullable User fallbackOwner, Map<String, User> cache) {
        if (username == null || username.isBlank()) {
            return fallbackOwner;
        }
        return cache.computeIfAbsent(username, name -> dataManager.load(User.class)
                .query("select u from umida_User u where u.username = :username")
                .parameter("username", name)
                .optional()
                .orElse(fallbackOwner));
    }
}
