package uz.kapitalbank.umida.service;

import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.reports.entity.Report;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.entity.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.ReportAccess;
import uz.kapitalbank.umida.entity.ReportAccessEvent;
import uz.kapitalbank.umida.entity.ReportAccessRequest;
import uz.kapitalbank.umida.entity.ReportAccessRow;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.UserReport;
import uz.kapitalbank.umida.enums.AccessEventType;
import uz.kapitalbank.umida.enums.AccessLevel;
import uz.kapitalbank.umida.enums.GrantorStatus;
import uz.kapitalbank.umida.enums.RequestScope;
import uz.kapitalbank.umida.enums.RequestStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ролевая модель доступа к отчётам: NONE (строки нет) → READ (запуск) → GRANT (запуск плюс право
 * выдавать доступ другим по этому отчёту). Владелец отчёта — {@code UserReport.owner}, он всё
 * может по определению.
 * <p>
 * Заявки решают владелец и адресат заявки, а данные при этом пишутся от имени системного
 * пользователя: у обычного сотрудника нет прав на чужие строки доступа, но именно он нажимает
 * кнопку. Поэтому все изменения идут через {@link SystemAuthenticator}, а проверку «кому вообще
 * можно» делает сам сервис — {@link #canDecide(User, Report)}.
 */
@Service
public class ReportAccessService {

    /**
     * Причина автоматического закрытия параллельных заявок — попадает в журнал. В базе лежит
     * ключ сообщения, а не готовый текст: журнал читают на трёх языках, перевод подставляется
     * при показе.
     */
    public static final String AUTO_CANCEL_REASON = "msg://autoCancelReason";

    private final DataManager dataManager;
    private final SystemAuthenticator systemAuthenticator;

    public ReportAccessService(DataManager dataManager, SystemAuthenticator systemAuthenticator) {
        this.dataManager = dataManager;
        this.systemAuthenticator = systemAuthenticator;
    }

    // ---------------------------------------------------------------- запросы

    /**
     * Создаёт заявку на доступ. Адресат — выбранный на вкладке «Доступы» сотрудник (владелец или
     * обладатель GRANT); решить её сможет он либо владелец отчёта.
     */
    public ReportAccessRequest request(User requester, Report report, @Nullable User addressee,
                                       String reason, RequestScope scope, AccessLevel requestedLevel) {
        return systemAuthenticator.withSystem(() -> {
            ReportAccessRequest request = dataManager.create(ReportAccessRequest.class);
            request.setRequester(requester);
            request.setReport(report);
            request.setAddressee(addressee);
            request.setReason(reason);
            request.setScope(scope);
            request.setRequestedLevel(requestedLevel);
            request.setStatus(RequestStatus.PENDING);
            request.setRequestedAt(LocalDateTime.now());

            ReportAccessEvent event = newEvent(report, AccessEventType.REQUEST_CREATED, requester, requester);
            event.setRequestReason(reason);

            SaveContext saveContext = new SaveContext().saving(request);
            ReportAccessRequest saved = dataManager.save(saveContext).get(request);
            event.setRequest(saved);
            dataManager.save(event);
            return saved;
        });
    }

    /**
     * Удовлетворяет заявку: заводит (или продлевает) доступ и закрывает заявку статусом SUCCESS.
     * Пустая {@code validTo} означает бессрочный доступ.
     */
    public void grant(ReportAccessRequest request, User decidedBy,
                      LocalDate validFrom, @Nullable LocalDate validTo, boolean canGrant) {
        systemAuthenticator.withSystem(() -> {
            ReportAccessRequest managed = reload(request);
            User subject = managed.getRequester();
            Report report = managed.getReport();

            ReportAccess access = activeAccess(subject, report);
            boolean update = access != null;
            if (!update) {
                access = dataManager.create(ReportAccess.class);
                access.setReport(report);
                access.setUser(subject);
            }
            access.setAccessLevel(canGrant ? AccessLevel.GRANT : AccessLevel.READ);
            access.setValidFrom(validFrom);
            access.setValidTo(validTo);
            access.setActive(true);
            access.setGrantedBy(decidedBy);
            access.setGrantedAt(LocalDateTime.now());
            access.setRevokedBy(null);
            access.setRevokedAt(null);
            access.setRevokeReason(null);

            managed.setStatus(RequestStatus.SUCCESS);
            managed.setDecidedBy(decidedBy);
            managed.setDecidedAt(LocalDateTime.now());
            managed.setValidFrom(validFrom);
            managed.setValidTo(validTo);
            managed.setCanGrant(canGrant);

            ReportAccessEvent event = newEvent(report,
                    update ? AccessEventType.ACCESS_CHANGED : AccessEventType.ACCESS_GRANTED,
                    subject, decidedBy);
            event.setRequest(managed);
            event.setRequestReason(managed.getReason());
            event.setValidFrom(validFrom);
            event.setValidTo(validTo);

            SaveContext saveContext = new SaveContext().saving(access, managed, event);

            // Заявитель мог попросить у нескольких сотрудников сразу: как только доступ выдан,
            // остальные заявки закрываются, иначе доступ выдали бы повторно.
            for (ReportAccessRequest other : pendingRequestsOf(subject, report)) {
                if (other.getId().equals(managed.getId())) {
                    continue;
                }
                other.setStatus(RequestStatus.CANCEL);
                other.setDecidedBy(decidedBy);
                other.setDecidedAt(LocalDateTime.now());
                other.setRejectionReason(AUTO_CANCEL_REASON);
                ReportAccessEvent autoEvent = newEvent(report, AccessEventType.ACCESS_REJECTED,
                        subject, decidedBy);
                autoEvent.setRequest(other);
                autoEvent.setRequestReason(other.getReason());
                autoEvent.setRejectionReason(AUTO_CANCEL_REASON);
                saveContext.saving(other, autoEvent);
            }

            dataManager.save(saveContext);
            return null;
        });
    }

    /**
     * Нерешённые заявки сотрудника по отчёту — своих может быть несколько, к разным адресатам.
     */
    private List<ReportAccessRequest> pendingRequestsOf(User requester, Report report) {
        return dataManager.load(ReportAccessRequest.class)
                .query("select e from ReportAccessRequest e where e.report = :report"
                        + " and e.requester = :requester and e.status = :status")
                .parameter("report", report)
                .parameter("requester", requester)
                .parameter("status", RequestStatus.PENDING.getId())
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("addressee", u -> u.addFetchPlan("_base")))
                .list();
    }

    /**
     * Отказ по заявке — причина обязательна, она показывается заявителю в истории.
     */
    public void reject(ReportAccessRequest request, User decidedBy, String rejectionReason) {
        systemAuthenticator.withSystem(() -> {
            ReportAccessRequest managed = reload(request);
            managed.setStatus(RequestStatus.CANCEL);
            managed.setDecidedBy(decidedBy);
            managed.setDecidedAt(LocalDateTime.now());
            managed.setRejectionReason(rejectionReason);

            ReportAccessEvent event = newEvent(managed.getReport(), AccessEventType.ACCESS_REJECTED,
                    managed.getRequester(), decidedBy);
            event.setRequest(managed);
            event.setRequestReason(managed.getReason());
            event.setRejectionReason(rejectionReason);

            dataManager.save(new SaveContext().saving(managed, event));
            return null;
        });
    }

    /**
     * Отзыв уже выданного доступа. Заявка, по которой он выдавался, переходит в REVOKE, чтобы в
     * истории было видно, чем закончилось.
     */
    public void revoke(ReportAccess access, User revokedBy, @Nullable String reason) {
        systemAuthenticator.withSystem(() -> {
            ReportAccess managed = dataManager.load(ReportAccess.class)
                    .id(access.getId())
                    .fetchPlan(fp -> fp.addFetchPlan("_base").add("report").add("user"))
                    .one();
            managed.setActive(false);
            managed.setRevokedBy(revokedBy);
            managed.setRevokedAt(LocalDateTime.now());
            managed.setRevokeReason(reason);

            ReportAccessEvent event = newEvent(managed.getReport(), AccessEventType.ACCESS_REVOKED,
                    managed.getUser(), revokedBy);
            event.setRejectionReason(reason);
            // Период — тот, который отзывается: иначе колонка «Период» у строки отзыва пустая,
            // хотя доступ выдавался на срок.
            event.setValidFrom(managed.getValidFrom());
            event.setValidTo(managed.getValidTo());

            SaveContext saveContext = new SaveContext().saving(managed, event);

            ReportAccessRequest last = lastDecidedRequest(managed.getUser(), managed.getReport());
            if (last != null) {
                // Заявка, по которой доступ выдавали: с неё берутся ссылка и причина запроса,
                // чтобы строка отзыва в журнале была заполнена так же, как выдача.
                event.setRequest(last);
                event.setRequestReason(last.getReason());
            }
            if (last != null && last.getStatus() == RequestStatus.SUCCESS) {
                last.setStatus(RequestStatus.REVOKE);
                last.setRejectionReason(reason);
                saveContext.saving(last);
            }

            dataManager.save(saveContext);
            return null;
        });
    }

    // ---------------------------------------------------------------- чтение

    /**
     * Есть ли у сотрудника право запускать отчёт: владелец, либо активный доступ, срок которого
     * ещё не истёк.
     */
    public boolean canRun(User user, Report report) {
        return level(user, report) != AccessLevel.NONE;
    }

    /**
     * Уровень доступа сотрудника к отчёту с учётом владельца и срока действия.
     */
    public AccessLevel level(User user, Report report) {
        if (isOwner(user, report)) {
            return AccessLevel.OWNER;
        }
        ReportAccess access = activeAccess(user, report);
        if (access == null || !withinPeriod(access)) {
            return AccessLevel.NONE;
        }
        return access.getAccessLevel();
    }

    /**
     * Может ли сотрудник решать заявки по отчёту: владелец или обладатель GRANT.
     */
    public boolean canDecide(User user, Report report) {
        AccessLevel level = level(user, report);
        return level == AccessLevel.OWNER || level == AccessLevel.GRANT;
    }

    public boolean isOwner(User user, Report report) {
        return systemAuthenticator.withSystem(() -> !dataManager.load(UserReport.class)
                .query("select e from UserReport e where e.report = :report and e.owner = :owner")
                .parameter("report", report)
                .parameter("owner", user)
                .maxResults(1)
                .list()
                .isEmpty());
    }

    @Nullable
    private User owner(Report report) {
        return systemAuthenticator.withSystem(() -> dataManager.load(UserReport.class)
                .query("select e from UserReport e where e.report = :report")
                .parameter("report", report)
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("owner", o -> o.addFetchPlan("_base")))
                .maxResults(1)
                .list()
                .stream()
                .map(UserReport::getOwner)
                .findFirst()
                .orElse(null));
    }

    /**
     * У кого можно просить доступ: владелец отчёта плюс сотрудники с активным уровнем GRANT.
     * Заявитель из списка исключается — самому себе доступ не просят.
     */
    private List<User> grantors(Report report, @Nullable User exclude) {
        return systemAuthenticator.withSystem(() -> {
            Set<User> result = new LinkedHashSet<>();
            User owner = owner(report);
            if (owner != null) {
                result.add(owner);
            }
            dataManager.load(ReportAccess.class)
                    .query("select e from ReportAccess e where e.report = :report"
                            + " and e.active = true and e.accessLevel = :level")
                    .parameter("report", report)
                    .parameter("level", AccessLevel.GRANT.getId())
                    .fetchPlan(fp -> fp.addFetchPlan("_base").add("user", u -> u.addFetchPlan("_base")))
                    .list()
                    .stream()
                    .filter(this::withinPeriod)
                    .map(ReportAccess::getUser)
                    .forEach(result::add);
            if (exclude != null) {
                result.removeIf(user -> user.getId().equals(exclude.getId()));
            }
            return new ArrayList<>(result);
        });
    }

    /**
     * Все действующие доступы по отчёту — вкладка «Доступы» у владельца.
     */
    private List<ReportAccess> accesses(Report report) {
        return systemAuthenticator.withSystem(() -> dataManager.load(ReportAccess.class)
                .query("select e from ReportAccess e where e.report = :report and e.active = true"
                        + " order by e.grantedAt desc")
                .parameter("report", report)
                .fetchPlan(fp -> fp.addFetchPlan("_base")
                        .add("user", u -> u.addFetchPlan("_base"))
                        .add("grantedBy", u -> u.addFetchPlan("_base")))
                .list());
    }

    /**
     * Нерешённые заявки по отчёту — вкладка «Запросы» и счётчик на ней.
     */
    public List<ReportAccessRequest> pendingRequests(Report report) {
        return systemAuthenticator.withSystem(() -> dataManager.load(ReportAccessRequest.class)
                .query("select e from ReportAccessRequest e where e.report = :report"
                        + " and e.status = :status order by e.requestedAt desc")
                .parameter("report", report)
                .parameter("status", RequestStatus.PENDING.getId())
                .fetchPlan(fp -> fp.addFetchPlan("_base")
                        .add("requester", u -> u.addFetchPlan("_base"))
                        .add("addressee", u -> u.addFetchPlan("_base")))
                .list());
    }

    /**
     * Журнал по отчёту. Обычный сотрудник видит только свои события, владелец и делегат — все.
     */
    public List<ReportAccessEvent> history(Report report, @Nullable User onlyFor) {
        return systemAuthenticator.withSystem(() -> {
            String query = "select e from ReportAccessEvent e where e.report = :report"
                    + (onlyFor == null ? "" : " and e.subjectUser = :subject")
                    + " order by e.eventDate desc";
            var loader = dataManager.load(ReportAccessEvent.class)
                    .query(query)
                    .parameter("report", report)
                    // Должность и подразделение — колонки по пути subjectUser.orgEmployee.*,
                    // поэтому строка оргструктуры тянется тем же запросом.
                    .fetchPlan(fp -> fp.addFetchPlan("_base")
                            .add("subjectUser", u -> u.addFetchPlan("_base")
                                    .add("orgEmployee", e -> e.addFetchPlan("_base")))
                            .add("actorUser", u -> u.addFetchPlan("_base")
                                    .add("orgEmployee", e -> e.addFetchPlan("_base"))));
            if (onlyFor != null) {
                loader = loader.parameter("subject", onlyFor);
            }
            return loader.list();
        });
    }

    /**
     * Идентификаторы отчётов, которые сотрудник вправе запускать: свои плюс выданные ему.
     */
    public Set<UUID> availableReportIds(User user) {
        return systemAuthenticator.withSystem(() -> {
            Set<UUID> ids = dataManager.load(UserReport.class)
                    .query("select e from UserReport e where e.owner = :owner")
                    .parameter("owner", user)
                    .list()
                    .stream()
                    .map(userReport -> userReport.getReport().getId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            dataManager.load(ReportAccess.class)
                    .query("select e from ReportAccess e where e.user = :user and e.active = true")
                    .parameter("user", user)
                    .list()
                    .stream()
                    .filter(this::withinPeriod)
                    .forEach(access -> ids.add(access.getReport().getId()));
            return ids;
        });
    }

    /**
     * Строки для таблиц экрана «Доступы»: владелец отчёта первым, затем действующие доступы.
     */
    public List<ReportAccessRow> accessRows(Report report) {
        return systemAuthenticator.withSystem(() -> {
            List<ReportAccessRow> rows = new ArrayList<>();
            User owner = owner(report);
            if (owner != null) {
                rows.add(row(owner, AccessLevel.OWNER, null, null, null));
            }
            for (ReportAccess access : accesses(report)) {
                if (owner != null && owner.getId().equals(access.getUser().getId())) {
                    continue;
                }
                rows.add(row(access.getUser(), access.getAccessLevel(),
                        access.getValidFrom(), access.getValidTo(), access.getId()));
            }
            return rows;
        });
    }

    /**
     * Строки вкладки «Доступы» у обычного сотрудника: у кого можно попросить доступ, кто уже выдал
     * текущий доступ и к кому заявка ещё на рассмотрении.
     */
    public List<ReportAccessRow> grantorRows(Report report, @Nullable User requester) {
        return systemAuthenticator.withSystem(() -> {
            User owner = owner(report);
            ReportAccess current = requester == null ? null : activeAccess(requester, report);
            UUID grantedById = current == null || current.getGrantedBy() == null
                    ? null : current.getGrantedBy().getId();
            Set<UUID> pendingAddressees = requester == null ? Set.of() : pendingRequestsOf(requester, report)
                    .stream()
                    .filter(request -> request.getAddressee() != null)
                    .map(request -> request.getAddressee().getId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            List<ReportAccessRow> rows = new ArrayList<>();
            for (User user : grantors(report, requester)) {
                boolean isOwner = owner != null && owner.getId().equals(user.getId());
                ReportAccessRow row = row(user, isOwner ? AccessLevel.OWNER : AccessLevel.GRANT,
                        null, null, null);
                if (user.getId().equals(grantedById)) {
                    row.setGrantorStatus(GrantorStatus.GRANTED);
                } else if (pendingAddressees.contains(user.getId())) {
                    row.setGrantorStatus(GrantorStatus.PENDING);
                } else {
                    row.setGrantorStatus(GrantorStatus.NONE);
                }
                rows.add(row);
            }
            return rows;
        });
    }

    /**
     * Можно ли просить доступ у выбранного сотрудника.
     * <p>
     * Нельзя, когда доступ уже есть — любого уровня, включая READ, — и когда к этому же сотруднику
     * заявка уже отправлена. Заявка, висящая на другом сотруднике, не мешает: просить можно у
     * нескольких, а лишние закроются, как только один выдаст доступ.
     */
    public boolean canRequestFrom(User requester, Report report, ReportAccessRow grantorRow) {
        if (level(requester, report) != AccessLevel.NONE) {
            return false;
        }
        return grantorRow.getGrantorStatus() != GrantorStatus.PENDING;
    }

    /**
     * Уровень, который просят заявкой: с действующим READ это повышение до GRANT, иначе READ.
     */
    public AccessLevel requestedLevel(User requester, Report report) {
        return level(requester, report) == AccessLevel.READ ? AccessLevel.GRANT : AccessLevel.READ;
    }

    private ReportAccessRow row(User user, AccessLevel level,
                                @Nullable LocalDate validFrom, @Nullable LocalDate validTo,
                                @Nullable UUID accessId) {
        ReportAccessRow row = dataManager.create(ReportAccessRow.class);
        row.setId(UUID.randomUUID());
        row.setUserId(user.getId());
        row.setAccessId(accessId);
        row.setUsername(user.getUsername());
        row.setFullName(displayName(user));
        row.setAccessLevel(level);
        row.setValidFrom(validFrom);
        row.setValidTo(validTo);
        return row;
    }

    /**
     * ФИО сотрудника; пока справочника должностей нет, при пустом имени показывается логин.
     */
    public static String displayName(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String name = (last + " " + first).trim();
        return name.isEmpty() ? user.getUsername() : name;
    }

    /**
     * Должность и подразделение сотрудника — колонки вкладки «История».
     */
    public record EmployeeInfo(String position, String department) {
    }

    /**
     * Справочные данные сотрудников одним запросом: журнал показывает должность и подразделение,
     * но хранит только ссылку на пользователя, поэтому они берутся из оргструктуры на момент
     * показа. Возвращаются простые строки, а не сущности — у обычного сотрудника прав на
     * справочник нет, чтение идёт под системным пользователем.
     */
    public Map<UUID, EmployeeInfo> employeeInfo(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return systemAuthenticator.withSystem(() -> {
            Map<UUID, EmployeeInfo> result = new HashMap<>();
            dataManager.load(OrgStructureEmployee.class)
                    .query("select e from OrgStructureEmployee e where e.user.id in :ids")
                    .parameter("ids", userIds)
                    .fetchPlan(fp -> fp.addFetchPlan("_base").add("user", u -> u.addFetchPlan("_base")))
                    .list()
                    .forEach(employee -> {
                        if (employee.getUser() != null) {
                            result.putIfAbsent(employee.getUser().getId(),
                                    new EmployeeInfo(employee.getPositionName(), employee.getDepartment()));
                        }
                    });
            return result;
        });
    }

    /**
     * Загружает пользователя по идентификатору строки таблицы.
     */
    public User user(UUID userId) {
        return systemAuthenticator.withSystem(() -> dataManager.load(User.class).id(userId).one());
    }

    // ---------------------------------------------------------------- служебное

    @Nullable
    private ReportAccess activeAccess(User user, Report report) {
        return systemAuthenticator.withSystem(() -> dataManager.load(ReportAccess.class)
                .query("select e from ReportAccess e where e.report = :report"
                        + " and e.user = :user and e.active = true")
                .parameter("report", report)
                .parameter("user", user)
                .fetchPlan(fp -> fp.addFetchPlan("_base")
                        .add("user", u -> u.addFetchPlan("_base"))
                        .add("report", r -> r.addFetchPlan("_base")))
                .maxResults(1)
                .list()
                .stream()
                .findFirst()
                .orElse(null));
    }

    @Nullable
    private ReportAccessRequest lastDecidedRequest(User user, Report report) {
        return dataManager.load(ReportAccessRequest.class)
                .query("select e from ReportAccessRequest e where e.report = :report"
                        + " and e.requester = :user and e.decidedAt is not null"
                        + " order by e.decidedAt desc")
                .parameter("report", report)
                .parameter("user", user)
                .maxResults(1)
                .list()
                .stream()
                .findFirst()
                .orElse(null);
    }

    private boolean withinPeriod(ReportAccess access) {
        LocalDate today = LocalDate.now();
        return (access.getValidFrom() == null || !access.getValidFrom().isAfter(today))
                && (access.getValidTo() == null || !access.getValidTo().isBefore(today));
    }

    private ReportAccessRequest reload(ReportAccessRequest request) {
        return dataManager.load(ReportAccessRequest.class)
                .id(request.getId())
                .fetchPlan(fp -> fp.addFetchPlan("_base")
                        .add("requester", u -> u.addFetchPlan("_base"))
                        .add("report", r -> r.addFetchPlan("_base")))
                .one();
    }

    private ReportAccessEvent newEvent(Report report, AccessEventType type, User subject, User actor) {
        ReportAccessEvent event = dataManager.create(ReportAccessEvent.class);
        event.setReport(report);
        event.setEventDate(LocalDateTime.now());
        event.setEventType(type);
        event.setSubjectUser(subject);
        event.setActorUser(actor);
        return event;
    }
}
