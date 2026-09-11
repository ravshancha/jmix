package uz.kapitalbank.umida.view.reporting;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.jmix.reportsflowui.view.group.ReportGroupDetailView;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.entity.ExtReportGroup;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Редактор группы отчётов: к полям аддона добавлены иконка, цвет и карточка-превью, по которой
 * сразу видно, как группа встанет в список.
 * <p>
 * Наследует {@link ReportGroupDetailView}, поэтому проверка уникальности системного кода и
 * блокировка сохранения для групп, объявленных аннотацией, работают как в аддоне. Иконка и цвет
 * — поля {@link ExtReportGroup}, которая подменяет группу аддона, поэтому они сохраняются вместе
 * с самой группой.
 */
@Route(value = "report-group/:id", layout = MainView.class)
@ViewController("umida_ReportGroupDetailView")
@ViewDescriptor("report-group-detail-view.xml")
@EditedEntityContainer("groupDc")
@DialogMode(width = "34em")
public class ExtReportGroupDetailView extends ReportGroupDetailView {

    @ViewComponent
    protected Div previewCard;
    @ViewComponent
    protected Div iconPicker;
    @ViewComponent
    protected Div colorPicker;
    @ViewComponent
    protected TypedTextField<String> titleField;


    protected final Map<String, Div> iconTiles = new LinkedHashMap<>();
    protected final Map<String, Div> colorTiles = new LinkedHashMap<>();

    protected String selectedIcon = ReportGroupStyles.DEFAULT_ICON;
    protected String selectedColor = ReportGroupStyles.COLORS.get(0);

    @Subscribe
    public void onInit(final InitEvent event) {
        buildIconPicker();
        buildColorPicker();
        // Название печатают руками, а карточка должна показывать его сразу.
        titleField.addTypedValueChangeListener(e -> refreshPreview());
    }

    /**
     * У сохранённой группы оформление уже выбрано — оно и подставляется; у новой остаются
     * значения по умолчанию (папка и первый цвет палитры).
     */
    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        ExtReportGroup group = editedGroup();
        if (group.getIcon() != null) {
            selectedIcon = group.getIcon();
        }
        if (group.getColor() != null) {
            selectedColor = group.getColor();
        }
        highlightIcon();
        highlightColor();
        refreshPreview();
    }

    /**
     * Оформление — поля самой группы, поэтому просто переносится в сущность перед сохранением.
     */
    @Subscribe
    public void onBeforeSaveStyle(final BeforeSaveEvent event) {
        editedGroup().setIcon(selectedIcon);
        editedGroup().setColor(selectedColor);
    }

    /**
     * Редактируемая группа как расширенная сущность: {@code ReportGroup} подменён
     * {@link ExtReportGroup}, поэтому проверять тип не нужно.
     */
    protected ExtReportGroup editedGroup() {
        return (ExtReportGroup) getEditedEntity();
    }

    protected void buildIconPicker() {
        for (String iconName : ReportGroupStyles.ICONS) {
            Div tile = new Div();
            tile.addClassName("report-group-icon-tile");
            tile.add(VaadinIcon.valueOf(iconName).create());
            tile.addClickListener(e -> {
                selectedIcon = iconName;
                highlightIcon();
                refreshPreview();
            });
            iconTiles.put(iconName, tile);
            iconPicker.add(tile);
        }
    }

    protected void buildColorPicker() {
        for (String color : ReportGroupStyles.COLORS) {
            Div tile = new Div();
            tile.addClassName("report-group-color-tile");
            tile.getStyle().set("background", color);
            // Галочка нарисована всегда, а показывается только на выбранном цвете — так тайл не
            // меняет размер при выборе.
            tile.add(VaadinIcon.CHECK.create());
            tile.addClickListener(e -> {
                selectedColor = color;
                highlightColor();
                refreshPreview();
            });
            colorTiles.put(color, tile);
            colorPicker.add(tile);
        }
    }

    protected void highlightIcon() {
        iconTiles.forEach((name, tile) ->
                tile.getElement().getClassList().set("selected", name.equals(selectedIcon)));
    }

    protected void highlightColor() {
        colorTiles.forEach((color, tile) ->
                tile.getElement().getClassList().set("selected", color.equals(selectedColor)));
    }

    protected void refreshPreview() {
        previewCard.removeAll();
        previewCard.getStyle().set("--report-group-accent", selectedColor);

        Div iconBox = new Div();
        iconBox.addClassName("report-group-card-icon");
        iconBox.add(VaadinIcon.valueOf(selectedIcon).create());

        // Пока название не введено, в карточке остаётся одна иконка — подставлять сюда
        // техническое имя сущности бессмысленно.
        String title = titleField.getTypedValue();
        Span titleSpan = new Span(title == null ? "" : title.trim());
        titleSpan.addClassName("report-group-card-title");

        previewCard.add(iconBox, titleSpan);
    }
}
