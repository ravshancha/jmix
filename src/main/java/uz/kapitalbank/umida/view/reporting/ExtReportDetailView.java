package uz.kapitalbank.umida.view.reporting;

import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import io.jmix.core.EntityStates;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.view.DefaultMainViewParent;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.jmix.reports.entity.ReportGroup;
import io.jmix.reportsflowui.view.report.ReportDetailView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.security.core.userdetails.UserDetails;
import uz.kapitalbank.umida.entity.ReportDomain;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.service.UserReportSyncService;

/**
 * Экран отчёта аддона плюс поле «Домен».
 * <p>
 * Отдельного диалога для создания отчёта нет: и «Создать», и мастер, и «Изменить» открывают этот
 * экран, поэтому домен добавлен в него, а не рядом с ним. Идентификатор {@code report_Report.detail}
 * совпадает с аддоновским намеренно — вью реестр перезаписывает экран аддона экраном приложения, а
 * дескриптор наследуется через {@code extends} и содержит только добавленное поле. По той же
 * причине аннотации маршрута продублированы: аннотации не наследуются, а экран аддона в реестре
 * больше не участвует.
 * <p>
 * Домен живёт на строке {@code UserReport}, а не на самом отчёте (см.
 * {@link uz.kapitalbank.umida.entity.UserReport#getDomain()}), поэтому читается и сохраняется здесь
 * вручную, вокруг обычного сохранения отчёта.
 */
@Route(value = "report/reports/:id", layout = DefaultMainViewParent.class)
@RouteAlias(value = "reports/:id", layout = DefaultMainViewParent.class)
@ViewController("report_Report.detail")
@ViewDescriptor("ext-report-detail-view.xml")
@EditedEntityContainer("reportDc")
public class ExtReportDetailView extends ReportDetailView {

    @Autowired
    private EntityStates entityStates;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private UserReportSyncService userReportSyncService;

    /**
     * Форма вкладки «Отчёт» из аддона: нужна, чтобы убрать из неё аддоновский выбор группы.
     */
    @ViewComponent
    private FormLayout reportForm;

    /**
     * Выпадающий список групп из аддона. Плоский список группам не подходит — они вложенные,
     * поэтому поле убирается с формы, а вместо него показывается {@code groupPickerField}.
     */
    @ViewComponent
    private EntityComboBox<ReportGroup> groupField;

    /**
     * Поле выбора области: значение приходит из справочника {@code umida_ReportDomain.list},
     * который открывается диалогом действием {@code lookup}. Списком значений здесь не
     * управляют — у областей есть поддомены, и дерево справочника показывает их само.
     */
    @ViewComponent
    private EntityPicker<ReportDomain> domainField;

    /**
     * Заменить компонент наследованием дескриптора нельзя — расширение меняет только атрибуты
     * элемента, но не его тип, поэтому аддоновское поле снимается с формы здесь.
     */
    @Subscribe
    public void onInitRemoveAddonGroupField(InitEvent event) {
        reportForm.remove(groupField);
    }

    /**
     * У нового отчёта строки «Отчётности» ещё нет, поэтому читать нечего: поле остаётся пустым до
     * первого сохранения.
     */
    @Subscribe
    public void onBeforeShowDomainField(BeforeShowEvent event) {
        if (!entityStates.isNew(getEditedEntity())) {
            domainField.setValue(userReportSyncService.domainOf(getEditedEntity().getId()));
        }
    }

    /**
     * Сохранять домен раньше самого отчёта нельзя: строка «Отчётности» ссылается на отчёт, а он до
     * коммита ещё не существует.
     */
    @Subscribe
    public void onAfterSaveDomainField(AfterSaveEvent event) {
        userReportSyncService.assignDomain(getEditedEntity().getId(), domainField.getValue(), currentUser());
    }

    @Nullable
    private User currentUser() {
        UserDetails userDetails = currentAuthentication.getUser();
        return userDetails instanceof User user ? user : null;
    }
}
