package uz.kapitalbank.umida.service;

import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.accesscontext.SpecificOperationAccessContext;
import io.jmix.core.AccessManager;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.reports.entity.Report;
import io.jmix.security.model.ResourcePolicy;
import io.jmix.security.model.ResourcePolicyEffect;
import io.jmix.security.model.ResourcePolicyType;
import io.jmix.security.model.ResourceRole;
import io.jmix.security.role.ResourceRoleRepository;
import io.jmix.security.role.assignment.RoleAssignment;
import io.jmix.security.role.assignment.RoleAssignmentRepository;
import org.springframework.lang.Nullable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.entity.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.ReportAccess;
import uz.kapitalbank.umida.entity.ReportAccessEvent;
import uz.kapitalbank.umida.entity.ReportAccessRequest;
import uz.kapitalbank.umida.entity.ReportAccessRow;
import uz.kapitalbank.umida.entity.ReportDomain;
import uz.kapitalbank.umida.entity.ReportSubdivisionAccess;
import uz.kapitalbank.umida.entity.ReportSubdivisionRow;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.UserReport;
import uz.kapitalbank.umida.entity.OrgStructureSubdivision;
import uz.kapitalbank.umida.enums.AccessEventType;
import uz.kapitalbank.umida.enums.AccessLevel;
import uz.kapitalbank.umida.enums.GrantorStatus;
import uz.kapitalbank.umida.enums.RequestScope;
import uz.kapitalbank.umida.enums.RequestStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Ролевая модель доступа к отчётам: NONE (строки нет) → READ (запуск) → GRANT (запуск плюс право
 * выдавать доступ другим по этому отчёту). Владелец отчёта — {@code UserReport.owner}, он всё
 * может по определению; наравне с ним распоряжаются доступами сотрудники подразделений-владельцев —
 * отчёта ({@code UserReport.subdivision}) и его области ({@code ReportDomain.subdivision}) — и
 * вложенных в них подразделений.
 * <p>
 * Кроме личных доступов есть доступ подразделения ({@link ReportSubdivisionAccess}): отчёт
 * запускают все сотрудники выбранного подразделения и вложенных в него. Принадлежность берётся из
 * оргструктуры — идентификаторов уровней у {@link OrgStructureEmployee} и иерархии
 * {@link OrgStructureSubdivision}.
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

    /**
     * Право администратора на модель доступа: он видит и решает всё по любому отчёту, как если бы
     * был его владельцем. Отдельного разрешения никому не выдают — оно приходит из
     * {@code system-full-access} с его политикой {@code resources = "*"}.
     */
    public static final String ADMINISTER_REPORT_ACCESS = "reportAccess.administer";

    private final DataManager dataManager;
    private final SystemAuthenticator systemAuthenticator;
    private final CurrentAuthentication currentAuthentication;
    private final AccessManager accessManager;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final ResourceRoleRepository resourceRoleRepository;

    public ReportAccessService(DataManager dataManager, SystemAuthenticator systemAuthenticator,
                               CurrentAuthentication currentAuthentication, AccessManager accessManager,
                               RoleAssignmentRepository roleAssignmentRepository,
                               ResourceRoleRepository resourceRoleRepository) {
        this.dataManager = dataManager;
        this.systemAuthenticator = systemAuthenticator;
        this.currentAuthentication = currentAuthentication;
        this.accessManager = accessManager;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.resourceRoleRepository = resourceRoleRepository;
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
            fillAccess(access, decidedBy, validFrom, validTo, canGrant);

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
     * Выдача (или настройка) доступа без заявки — владелец отчёта, сотрудник подразделения-владельца или
     * администратор выбирает сотрудника на вкладке «Все». Если у сотрудника висит заявка, она
     * закрывается этим решением, как при одобрении со вкладки «Запросы».
     */
    public void grantDirect(Report report, User subject, User decidedBy,
                            LocalDate validFrom, @Nullable LocalDate validTo, boolean canGrant) {
        requireManager(decidedBy, report);
        List<ReportAccessRequest> pending = systemAuthenticator.withSystem(() -> pendingRequestsOf(subject, report));
        if (!pending.isEmpty()) {
            grant(pending.get(0), decidedBy, validFrom, validTo, canGrant);
            return;
        }
        systemAuthenticator.withSystem(() -> {
            ReportAccess access = activeAccess(subject, report);
            boolean update = access != null;
            if (!update) {
                access = dataManager.create(ReportAccess.class);
                access.setReport(report);
                access.setUser(subject);
            }
            fillAccess(access, decidedBy, validFrom, validTo, canGrant);

            ReportAccessEvent event = newEvent(report,
                    update ? AccessEventType.ACCESS_CHANGED : AccessEventType.ACCESS_GRANTED,
                    subject, decidedBy);
            event.setValidFrom(validFrom);
            event.setValidTo(validTo);

            dataManager.save(new SaveContext().saving(access, event));
            return null;
        });
    }

    private void fillAccess(ReportAccess access, User decidedBy,
                            LocalDate validFrom, @Nullable LocalDate validTo, boolean canGrant) {
        access.setAccessLevel(canGrant ? AccessLevel.GRANT : AccessLevel.READ);
        access.setValidFrom(validFrom);
        access.setValidTo(validTo);
        access.setActive(true);
        access.setGrantedBy(decidedBy);
        access.setGrantedAt(LocalDateTime.now());
        access.setRevokedBy(null);
        access.setRevokedAt(null);
        access.setRevokeReason(null);
    }

    /**
     * Выдача (или продление) доступа подразделению целиком. Уровень всегда READ — см.
     * {@link ReportSubdivisionAccess}.
     */
    public void grantSubdivision(Report report, OrgStructureSubdivision subdivision, User decidedBy,
                                 LocalDate validFrom, @Nullable LocalDate validTo) {
        requireManager(decidedBy, report);
        systemAuthenticator.withSystem(() -> {
            ReportSubdivisionAccess access = dataManager.load(ReportSubdivisionAccess.class)
                    .query("select e from ReportSubdivisionAccess e where e.report = :report"
                            + " and e.subdivision.id = :subdivisionId and e.active = true")
                    .parameter("report", report)
                    .parameter("subdivisionId", subdivision.getId())
                    .maxResults(1)
                    .list()
                    .stream()
                    .findFirst()
                    .orElse(null);
            boolean update = access != null;
            if (!update) {
                access = dataManager.create(ReportSubdivisionAccess.class);
                access.setReport(report);
                access.setSubdivision(subdivision);
            }
            access.setValidFrom(validFrom);
            access.setValidTo(validTo);
            access.setActive(true);
            access.setGrantedBy(decidedBy);
            access.setGrantedAt(LocalDateTime.now());

            ReportAccessEvent event = newEvent(report,
                    update ? AccessEventType.ACCESS_CHANGED : AccessEventType.ACCESS_GRANTED,
                    null, decidedBy);
            event.setSubdivision(subdivision);
            event.setValidFrom(validFrom);
            event.setValidTo(validTo);

            dataManager.save(new SaveContext().saving(access, event));
            return null;
        });
    }

    /**
     * Закрытие доступа подразделения: строка остаётся ради истории, сотрудники подразделения
     * теряют право запуска, если личного доступа у них нет.
     */
    public void revokeSubdivision(ReportSubdivisionAccess access, User revokedBy, @Nullable String reason) {
        requireManager(revokedBy, access.getReport());
        systemAuthenticator.withSystem(() -> {
            ReportSubdivisionAccess managed = dataManager.load(ReportSubdivisionAccess.class)
                    .id(access.getId())
                    .fetchPlan(fp -> fp.addFetchPlan("_base").add("report").add("subdivision"))
                    .one();
            managed.setActive(false);
            managed.setRevokedBy(revokedBy);
            managed.setRevokedAt(LocalDateTime.now());
            managed.setRevokeReason(reason);

            ReportAccessEvent event = newEvent(managed.getReport(), AccessEventType.ACCESS_REVOKED,
                    null, revokedBy);
            event.setSubdivision(managed.getSubdivision());
            event.setRejectionReason(reason);
            event.setValidFrom(managed.getValidFrom());
            event.setValidTo(managed.getValidTo());

            dataManager.save(new SaveContext().saving(managed, event));
            return null;
        });
    }

    /**
     * Прямая выдача и доступы подразделений минуют заявки, поэтому право проверяет сам сервис, а
     * не только экран.
     */
    private void requireManager(User user, Report report) {
        if (!canManage(user, report)) {
            throw new IllegalStateException("User " + user.getUsername()
                    + " is not allowed to manage access to report " + report.getId());
        }
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
        if (isAdministrator(user) || isOwner(user, report)) {
            return AccessLevel.OWNER;
        }
        if (isOwnerSubdivisionMember(user, report)) {
            return AccessLevel.SUBDIVISION_OWNER;
        }
        ReportAccess access = activeAccess(user, report);
        if (access != null && withinPeriod(access)) {
            return access.getAccessLevel();
        }
        // Личного доступа нет — отчёт всё равно запускается, если он выдан подразделению.
        return coveredBySubdivision(user, report) ? AccessLevel.READ : AccessLevel.NONE;
    }

    /**
     * Может ли сотрудник решать заявки по отчёту: владелец, подразделение-владелец, администратор
     * или обладатель GRANT.
     */
    public boolean canDecide(User user, Report report) {
        AccessLevel level = level(user, report);
        return level == AccessLevel.OWNER || level == AccessLevel.SUBDIVISION_OWNER || level == AccessLevel.GRANT;
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

    /**
     * Кто распоряжается доступами по отчёту — выдаёт без заявки, правит и закрывает, в том числе
     * подразделениям: владелец, сотрудники подразделения-владельца и администратор. Делегат с уровнем GRANT
     * доступ выдаёт только по заявке и не отзывает.
     */
    public boolean canManage(User user, Report report) {
        return isAdministrator(user) || isOwner(user, report) || isOwnerSubdivisionMember(user, report);
    }

    /**
     * Администратор ли переданный сотрудник — то есть тот, кому всё по любому отчёту видно и
     * решаемо наравне с владельцем.
     * <p>
     * Разрешение проверяется у текущей сессии, поэтому метод сначала убеждается, что спрашивают
     * именно про вошедшего пользователя. Заодно это страхует от вызова внутри
     * {@link SystemAuthenticator#withSystem}: там текущий пользователь — системный, у него прав
     * всегда хватает, и без сверки любой сотрудник оказался бы администратором.
     */
    public boolean isAdministrator(@Nullable User user) {
        if (user == null || !isCurrentUser(user)) {
            return false;
        }
        SpecificOperationAccessContext context =
                new SpecificOperationAccessContext(ADMINISTER_REPORT_ACCESS);
        accessManager.applyRegisteredConstraints(context);
        return context.isPermitted();
    }

    /**
     * Администратор ли произвольный сотрудник — не текущий, а, например, выбранный в таблице.
     * Сессии у него нет, поэтому право ищется в назначенных ему ролях: политика на
     * {@link #ADMINISTER_REPORT_ACCESS} или на все ресурсы сразу.
     */
    public boolean holdsAdministration(User user) {
        for (RoleAssignment assignment : roleAssignmentRepository.getAssignmentsByUsername(user.getUsername())) {
            ResourceRole role = resourceRoleRepository.findRoleByCode(assignment.getRoleCode());
            if (role != null && role.getAllResourcePolicies().stream().anyMatch(this::administers)) {
                return true;
            }
        }
        return false;
    }

    private boolean administers(ResourcePolicy policy) {
        return ResourcePolicyType.SPECIFIC.equals(policy.getType())
                && ResourcePolicyEffect.ALLOW.equals(policy.getEffect())
                && (ADMINISTER_REPORT_ACCESS.equals(policy.getResource()) || "*".equals(policy.getResource()));
    }

    private boolean isCurrentUser(User user) {
        UserDetails current = currentAuthentication.isSet() ? currentAuthentication.getUser() : null;
        return current instanceof User appUser && appUser.getId().equals(user.getId());
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
     * Подразделения-владельцы отчёта: его собственное ({@code UserReport.subdivision}) и
     * подразделения-владельцы его области и вышестоящих областей ({@code ReportDomain.subdivision}).
     * Связи независимы — заполнена может быть любая из них или обе.
     */
    private Set<String> ownerSubdivisionIds(Report report) {
        return systemAuthenticator.withSystem(() -> {
            Set<String> result = new LinkedHashSet<>();
            UserReport userReport = dataManager.load(UserReport.class)
                    .query("select e from UserReport e where e.report = :report")
                    .parameter("report", report)
                    .fetchPlan(fp -> fp.addFetchPlan("_base")
                            .add("subdivision", d -> d.addFetchPlan("_base"))
                            .add("domain", d -> d.addFetchPlan("_base")))
                    .maxResults(1)
                    .list()
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (userReport == null) {
                return result;
            }
            if (userReport.getSubdivision() != null) {
                result.add(userReport.getSubdivision().getId());
            }
            ReportDomain domain = userReport.getDomain();
            Set<UUID> seen = new HashSet<>();
            while (domain != null && seen.add(domain.getId())) {
                ReportDomain loaded = dataManager.load(ReportDomain.class)
                        .id(domain.getId())
                        .fetchPlan(fp -> fp.addFetchPlan("_base")
                                .add("parent", p -> p.addFetchPlan("_base"))
                                .add("subdivision", d -> d.addFetchPlan("_base")))
                        .one();
                if (loaded.getSubdivision() != null) {
                    result.add(loaded.getSubdivision().getId());
                }
                domain = loaded.getParent();
            }
            return result;
        });
    }

    /**
     * Сотрудники подразделений-владельцев и вложенных в них подразделений — те из них, у кого
     * есть вход в приложение.
     */
    private List<User> ownerSubdivisionMembers(Report report) {
        Set<String> owners = ownerSubdivisionIds(report);
        if (owners.isEmpty()) {
            return List.of();
        }
        return systemAuthenticator.withSystem(() -> {
            Set<String> ids = withDescendants(owners, subdivisionChildren());
            Map<UUID, User> result = new LinkedHashMap<>();
            dataManager.load(OrgStructureEmployee.class)
                    .query("select e from OrgStructureEmployee e where e.user is not null")
                    .fetchPlan(fp -> fp.addFetchPlan("_base").add("user", u -> u.addFetchPlan("_base")))
                    .list()
                    .stream()
                    .filter(employee -> levelIds(employee).stream().anyMatch(ids::contains))
                    .map(OrgStructureEmployee::getUser)
                    .sorted(Comparator.comparing(ReportAccessService::displayName, String.CASE_INSENSITIVE_ORDER))
                    .forEach(user -> result.putIfAbsent(user.getId(), user));
            return new ArrayList<>(result.values());
        });
    }

    /**
     * Сотрудник ли подразделения-владельца отчёта или его области (самого подразделения либо
     * вложенного в него).
     */
    public boolean isOwnerSubdivisionMember(User user, Report report) {
        Set<String> owners = ownerSubdivisionIds(report);
        if (owners.isEmpty()) {
            return false;
        }
        Set<String> own = systemAuthenticator.withSystem(() -> subdivisionsOf(user));
        return owners.stream().anyMatch(own::contains);
    }

    /**
     * Отчёты, где подразделение сотрудника (или вышестоящее) — владелец самого отчёта либо его
     * области; область берётся вместе со всеми поддоменами.
     */
    private Set<UUID> subdivisionOwnedReportIds(User user) {
        Set<String> subdivisions = subdivisionsOf(user);
        if (subdivisions.isEmpty()) {
            return Set.of();
        }
        Set<UUID> domains = dataManager.loadValues("select d.id from umida_ReportDomain d"
                        + " where d.subdivision.id in :subdivisions")
                .parameter("subdivisions", subdivisions)
                .properties("id")
                .list()
                .stream()
                .map(kv -> (UUID) kv.getValue("id"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!domains.isEmpty()) {
            Map<UUID, List<UUID>> children = new HashMap<>();
            dataManager.loadValues("select e.id, p.id from umida_ReportDomain e join e.parent p")
                    .properties("id", "parentId")
                    .list()
                    .forEach(kv -> children.computeIfAbsent(kv.getValue("parentId"), k -> new ArrayList<>())
                            .add(kv.getValue("id")));
            domains = withDescendants(domains, children);
        }
        Set<UUID> result = dataManager.load(UserReport.class)
                .query("select e from UserReport e where e.subdivision.id in :subdivisions")
                .parameter("subdivisions", subdivisions)
                .list()
                .stream()
                .map(userReport -> userReport.getReport().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!domains.isEmpty()) {
            dataManager.load(UserReport.class)
                    .query("select e from UserReport e where e.domain.id in :domains")
                    .parameter("domains", domains)
                    .list()
                    .forEach(userReport -> result.add(userReport.getReport().getId()));
        }
        return result;
    }

    /**
     * Дочерние подразделения по родителю — весь справочник одним запросом.
     */
    private Map<String, List<String>> subdivisionChildren() {
        Map<String, List<String>> children = new HashMap<>();
        dataManager.loadValues("select s.id, p.id from umida_OrgStructureSubdivision s join s.parent p")
                .properties("id", "parentId")
                .list()
                .forEach(kv -> children.computeIfAbsent(kv.getValue("parentId"), k -> new ArrayList<>())
                        .add(kv.getValue("id")));
        return children;
    }

    private static <K> Set<K> withDescendants(Collection<K> roots, Map<K, List<K>> children) {
        Set<K> result = new LinkedHashSet<>(roots);
        Deque<K> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            for (K child : children.getOrDefault(queue.poll(), List.of())) {
                if (result.add(child)) {
                    queue.add(child);
                }
            }
        }
        return result;
    }

    /**
     * Дерево вкладки «Подразделения»: весь справочник подразделений, у каждого — собственный
     * доступ к отчёту, если он выдан, либо вышестоящее подразделение, через которое доступ уже
     * есть. Справочник читается одним запросом, дерево собирается в памяти.
     */
    public List<ReportSubdivisionRow> subdivisionRows(Report report) {
        return systemAuthenticator.withSystem(() -> {
            Map<String, ReportSubdivisionAccess> accessBySubdivision = new HashMap<>();
            subdivisionAccesses(report).forEach(access ->
                    accessBySubdivision.putIfAbsent(access.getSubdivision().getId(), access));

            Map<String, ReportSubdivisionRow> rows = new LinkedHashMap<>();
            Map<String, String> parentIds = new HashMap<>();
            dataManager.loadValues("select s.id, s.name, p.id from umida_OrgStructureSubdivision s"
                            + " left join s.parent p order by s.name")
                    .properties("id", "name", "parentId")
                    .list()
                    .forEach(kv -> {
                        String id = kv.getValue("id");
                        String name = kv.getValue("name");
                        ReportSubdivisionRow row = dataManager.create(ReportSubdivisionRow.class);
                        row.setId(id);
                        row.setName(name == null ? id : name);
                        ReportSubdivisionAccess access = accessBySubdivision.get(id);
                        if (access != null) {
                            row.setAccessId(access.getId());
                            row.setValidFrom(access.getValidFrom());
                            row.setValidTo(access.getValidTo());
                            row.setGrantedBy(access.getGrantedBy() == null ? null : displayName(access.getGrantedBy()));
                            row.setGrantedAt(access.getGrantedAt());
                        }
                        rows.put(id, row);
                        parentIds.put(id, kv.getValue("parentId"));
                    });
            rows.forEach((id, row) -> row.setParent(rows.get(parentIds.get(id))));
            for (ReportSubdivisionRow row : rows.values()) {
                Set<String> seen = new HashSet<>();
                for (ReportSubdivisionRow up = row.getParent(); up != null && seen.add(up.getId()); up = up.getParent()) {
                    ReportSubdivisionAccess access = accessBySubdivision.get(up.getId());
                    if (access != null && withinPeriod(access.getValidFrom(), access.getValidTo())) {
                        row.setInheritedFrom(up.getName());
                        break;
                    }
                }
            }
            return new ArrayList<>(rows.values());
        });
    }

    /**
     * Строка доступа подразделения — для «Настроить» и «Закрыть» на вкладке «Подразделения».
     */
    public ReportSubdivisionAccess subdivisionAccess(UUID accessId) {
        return systemAuthenticator.withSystem(() -> dataManager.load(ReportSubdivisionAccess.class)
                .id(accessId)
                .fetchPlan(fp -> fp.addFetchPlan("_base")
                        .add("report", r -> r.addFetchPlan("_base"))
                        .add("subdivision", d -> d.addFetchPlan("_base")))
                .one());
    }

    /**
     * Действующие доступы подразделений по отчёту.
     */
    public List<ReportSubdivisionAccess> subdivisionAccesses(Report report) {
        return systemAuthenticator.withSystem(() -> dataManager.load(ReportSubdivisionAccess.class)
                .query("select e from ReportSubdivisionAccess e where e.report = :report and e.active = true"
                        + " order by e.grantedAt desc")
                .parameter("report", report)
                .fetchPlan(fp -> fp.addFetchPlan("_base")
                        .add("subdivision", s -> s.addFetchPlan("_base"))
                        .add("grantedBy", u -> u.addFetchPlan("_base")))
                .list());
    }

    /**
     * Есть ли у сотрудника доступ через подразделение: отчёт выдан его подразделению либо
     * вышестоящему.
     */
    private boolean coveredBySubdivision(User user, Report report) {
        List<ReportSubdivisionAccess> granted = subdivisionAccesses(report).stream()
                .filter(access -> withinPeriod(access.getValidFrom(), access.getValidTo()))
                .toList();
        if (granted.isEmpty()) {
            return false;
        }
        Set<String> own = systemAuthenticator.withSystem(() -> subdivisionsOf(user));
        return granted.stream().anyMatch(access -> own.contains(access.getSubdivision().getId()));
    }

    /**
     * Подразделения сотрудника вместе со всеми вышестоящими: уровни из его строки оргструктуры,
     * дополненные цепочкой родителей из справочника подразделений.
     */
    private Set<String> subdivisionsOf(User user) {
        OrgStructureEmployee employee = dataManager.load(OrgStructureEmployee.class)
                .query("select e from OrgStructureEmployee e where e.user = :user")
                .parameter("user", user)
                .maxResults(1)
                .list()
                .stream()
                .findFirst()
                .orElse(null);
        if (employee == null) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>(levelIds(employee));
        Set<String> frontier = new HashSet<>(result);
        while (!frontier.isEmpty()) {
            Set<String> next = new HashSet<>();
            dataManager.loadValues("select p.id from umida_OrgStructureSubdivision s join s.parent p"
                            + " where s.id in :ids")
                    .parameter("ids", frontier)
                    .properties("parentId")
                    .list()
                    .forEach(kv -> {
                        String parentId = kv.getValue("parentId");
                        if (result.add(parentId)) {
                            next.add(parentId);
                        }
                    });
            frontier = next;
        }
        return result;
    }

    /**
     * Идентификаторы подразделений всех уровней, к которым сотрудник отнесён в оргструктуре.
     */
    private static Set<String> levelIds(OrgStructureEmployee employee) {
        return Stream.of(employee.getOrganizationId(), employee.getDepartmentId(), employee.getDivisionId(),
                        employee.getSectionId(), employee.getUnitId())
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Сотрудники, у которых есть доступ через подразделение, с названием этого подразделения —
     * для вкладки «Все». Подразделения, которым выдан доступ, раскрываются вниз по иерархии
     * одним проходом по справочнику, чтобы не спрашивать базу про каждого сотрудника.
     */
    private Map<UUID, String> subdivisionCoverage(Report report) {
        List<ReportSubdivisionAccess> granted = subdivisionAccesses(report).stream()
                .filter(access -> withinPeriod(access.getValidFrom(), access.getValidTo()))
                .toList();
        if (granted.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> children = subdivisionChildren();
        Map<String, String> nameById = new HashMap<>();
        for (ReportSubdivisionAccess access : granted) {
            OrgStructureSubdivision subdivision = access.getSubdivision();
            String name = subdivision.getName() == null ? subdivision.getId() : subdivision.getName();
            withDescendants(List.of(subdivision.getId()), children)
                    .forEach(id -> nameById.putIfAbsent(id, name));
        }
        Map<UUID, String> result = new HashMap<>();
        dataManager.load(OrgStructureEmployee.class)
                .query("select e from OrgStructureEmployee e where e.user is not null")
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("user", u -> u.addFetchPlan("_base")))
                .list()
                .forEach(employee -> levelIds(employee).stream()
                        .map(nameById::get)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .ifPresent(name -> result.putIfAbsent(employee.getUser().getId(), name)));
        return result;
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
                                    .add("orgEmployee", e -> e.addFetchPlan("_base")))
                            .add("subdivision", s -> s.addFetchPlan("_base")));
            if (onlyFor != null) {
                loader = loader.parameter("subject", onlyFor);
            }
            return loader.list();
        });
    }

    /**
     * Идентификаторы отчётов, которые сотрудник вправе запускать: свои, выданные ему или его
     * подразделению, и отчёты, где владелец — его подразделение. Администратору — все.
     */
    public Set<UUID> availableReportIds(User user) {
        boolean administrator = isAdministrator(user);
        return systemAuthenticator.withSystem(() -> {
            if (administrator) {
                // Администратору доступен весь каталог, а не только свои и выданные отчёты.
                return dataManager.load(UserReport.class)
                        .all()
                        .list()
                        .stream()
                        .map(userReport -> userReport.getReport().getId())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            }
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
            Set<String> subdivisions = subdivisionsOf(user);
            if (!subdivisions.isEmpty()) {
                dataManager.load(ReportSubdivisionAccess.class)
                        .query("select e from ReportSubdivisionAccess e where e.active = true"
                                + " and e.subdivision.id in :subdivisions")
                        .parameter("subdivisions", subdivisions)
                        .list()
                        .stream()
                        .filter(access -> withinPeriod(access.getValidFrom(), access.getValidTo()))
                        .forEach(access -> ids.add(access.getReport().getId()));
            }
            ids.addAll(subdivisionOwnedReportIds(user));
            return ids;
        });
    }

    /**
     * Строки для таблиц экрана «Доступы»: владелец отчёта первым, за ним сотрудники подразделения-владельца,
     * затем действующие доступы, затем те, кто подал заявку и ждёт решения. У заявителя отметка
     * «Заявка отправлена»; если доступ у него уже есть (просит повышения), новой строки не
     * заводится — отметка ставится на его строку.
     */
    public List<ReportAccessRow> accessRows(Report report) {
        return systemAuthenticator.withSystem(() -> new ArrayList<>(accessRowsByUser(report).values()));
    }

    /**
     * Строки вкладки «Все» у распорядителя доступов: сверх {@link #accessRows(Report)} — все
     * действующие пользователи приложения, чтобы выдавать доступ сразу, без заявки. У тех, кому
     * отчёт доступен через подразделение, указано это подразделение.
     */
    public List<ReportAccessRow> allUserRows(Report report) {
        return systemAuthenticator.withSystem(() -> {
            Map<UUID, ReportAccessRow> byUser = accessRowsByUser(report);
            Map<UUID, String> coverage = subdivisionCoverage(report);
            byUser.values().forEach(row -> row.setSubdivisionName(coverage.get(row.getUserId())));

            List<ReportAccessRow> others = new ArrayList<>();
            dataManager.load(User.class)
                    .query("select e from umida_User e where e.active = true")
                    .list()
                    .stream()
                    .filter(user -> !byUser.containsKey(user.getId()))
                    .forEach(user -> {
                        String subdivision = coverage.get(user.getId());
                        ReportAccessRow row = row(user, subdivision == null ? AccessLevel.NONE : AccessLevel.READ,
                                null, null, null);
                        row.setSubdivisionName(subdivision);
                        others.add(row);
                    });
            // Сначала те, у кого доступ через подразделение, затем остальные — по алфавиту.
            others.sort(Comparator.comparing((ReportAccessRow row) -> row.getSubdivisionName() == null)
                    .thenComparing(ReportAccessRow::getFullName, String.CASE_INSENSITIVE_ORDER));
            List<ReportAccessRow> rows = new ArrayList<>(byUser.values());
            rows.addAll(others);
            return rows;
        });
    }

    private Map<UUID, ReportAccessRow> accessRowsByUser(Report report) {
        Map<UUID, ReportAccessRow> byUser = new LinkedHashMap<>();
        User owner = owner(report);
        if (owner != null) {
            byUser.put(owner.getId(), row(owner, AccessLevel.OWNER, null, null, null));
        }
        for (User member : ownerSubdivisionMembers(report)) {
            byUser.putIfAbsent(member.getId(), row(member, AccessLevel.SUBDIVISION_OWNER, null, null, null));
        }
        for (ReportAccess access : accesses(report)) {
            byUser.putIfAbsent(access.getUser().getId(), row(access.getUser(), access.getAccessLevel(),
                    access.getValidFrom(), access.getValidTo(), access.getId()));
        }
        for (ReportAccessRequest request : pendingRequests(report)) {
            ReportAccessRow row = byUser.computeIfAbsent(request.getRequester().getId(),
                    id -> row(request.getRequester(), AccessLevel.NONE, null, null, null));
            row.setGrantorStatus(GrantorStatus.PENDING);
        }
        return byUser;
    }

    /**
     * Строка доступа выбранного сотрудника — для диалога «Настроить доступ».
     */
    @Nullable
    public ReportAccess access(UUID accessId) {
        return systemAuthenticator.withSystem(() -> dataManager.load(ReportAccess.class)
                .id(accessId)
                .fetchPlan(fp -> fp.addFetchPlan("_base")
                        .add("user", u -> u.addFetchPlan("_base"))
                        .add("report", r -> r.addFetchPlan("_base")))
                .optional()
                .orElse(null));
    }

    /**
     * Строки вкладки «Доступы» у обычного сотрудника: те же, что у владельца, — доступы и ждущие
     * заявители, включая самого сотрудника. Дополнительно отмечено, кто выдал ему текущий доступ
     * и к кому ушла его заявка.
     */
    public List<ReportAccessRow> grantorRows(Report report, @Nullable User requester) {
        return systemAuthenticator.withSystem(() -> {
            ReportAccess current = requester == null ? null : activeAccess(requester, report);
            UUID grantedById = current == null || current.getGrantedBy() == null
                    ? null : current.getGrantedBy().getId();
            Set<UUID> pendingAddressees = requester == null ? Set.of() : pendingRequestsOf(requester, report)
                    .stream()
                    .filter(request -> request.getAddressee() != null)
                    .map(request -> request.getAddressee().getId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            List<ReportAccessRow> rows = accessRows(report);
            for (ReportAccessRow row : rows) {
                if (row.getGrantorStatus() == GrantorStatus.PENDING) {
                    continue;
                }
                if (row.getUserId().equals(grantedById)) {
                    row.setGrantorStatus(GrantorStatus.GRANTED);
                } else if (pendingAddressees.contains(row.getUserId())) {
                    row.setGrantorStatus(GrantorStatus.PENDING);
                } else {
                    row.setGrantorStatus(GrantorStatus.NONE);
                }
            }
            return rows;
        });
    }

    /**
     * Можно ли просить доступ у выбранного сотрудника.
     * <p>
     * Просят только у владельца отчёта или сотрудника подразделения-владельца: остальные строки списка лишь
     * показывают, у кого доступ есть. Нельзя, когда доступ уже есть — любого уровня, включая READ
     * и доступ через подразделение, — и когда заявка этому адресату уже отправлена и ещё не решена.
     */
    public boolean canRequestFrom(User requester, Report report, ReportAccessRow grantorRow) {
        boolean addresseeOwns = grantorRow.getAccessLevel() == AccessLevel.OWNER
                || grantorRow.getAccessLevel() == AccessLevel.SUBDIVISION_OWNER;
        if (!addresseeOwns || level(requester, report) != AccessLevel.NONE) {
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
        return withinPeriod(access.getValidFrom(), access.getValidTo());
    }

    private boolean withinPeriod(@Nullable LocalDate validFrom, @Nullable LocalDate validTo) {
        LocalDate today = LocalDate.now();
        return (validFrom == null || !validFrom.isAfter(today))
                && (validTo == null || !validTo.isBefore(today));
    }

    private ReportAccessRequest reload(ReportAccessRequest request) {
        return dataManager.load(ReportAccessRequest.class)
                .id(request.getId())
                .fetchPlan(fp -> fp.addFetchPlan("_base")
                        .add("requester", u -> u.addFetchPlan("_base"))
                        .add("report", r -> r.addFetchPlan("_base")))
                .one();
    }

    private ReportAccessEvent newEvent(Report report, AccessEventType type, @Nullable User subject, User actor) {
        ReportAccessEvent event = dataManager.create(ReportAccessEvent.class);
        event.setReport(report);
        event.setEventDate(LocalDateTime.now());
        event.setEventType(type);
        event.setSubjectUser(subject);
        event.setActorUser(actor);
        return event;
    }
}
