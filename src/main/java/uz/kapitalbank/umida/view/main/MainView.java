package uz.kapitalbank.umida.view.main;

import com.google.common.base.Strings;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.core.Messages;
import io.jmix.core.usersubstitution.CurrentUserSubstitution;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.app.main.StandardMainView;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.security.core.userdetails.UserDetails;
import uz.kapitalbank.umida.entity.User;

@Route("")
@ViewController(id = "umida_MainView")
@ViewDescriptor(path = "main-view.xml")
public class MainView extends StandardMainView {

    @Autowired
    private Messages messages;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private CurrentUserSubstitution currentUserSubstitution;

    @ViewComponent
    private Header header;
    @ViewComponent
    private DrawerToggle drawerToggle;

    /**
     * Место в шапке приложения под элементы текущего экрана. Создаётся здесь, а не в
     * {@code main-view.xml}: разметка шапки принадлежит главному экрану, а слот — служебный
     * контейнер, которым распоряжается только этот класс.
     */
    private HorizontalLayout viewActionsSlot;

    /**
     * Возвращает главный экран, в который встроен переданный экран, — через него экраны кладут
     * свои элементы в шапку приложения.
     */
    @Nullable
    public static MainView findMainView(View<?> origin) {
        return origin.getUI()
                .map(ui -> ui.getChildren()
                        .filter(MainView.class::isInstance)
                        .map(MainView.class::cast)
                        .findFirst()
                        .orElse(null))
                .orElse(null);
    }

    /**
     * Кладёт элементы экрана (например, панель вкладок) в шапку рядом с кнопкой меню.
     * При уходе с экрана нужно вызвать {@link #clearHeaderActions()}.
     */
    public void setHeaderActions(Component component) {
        HorizontalLayout slot = viewActionsSlot();
        slot.removeAll();
        if (component != null) {
            slot.add(component);
            slot.setWidthFull();
            slot.getElement().getStyle().set("flex-grow", "1");
        }
    }

    public void clearHeaderActions() {
        HorizontalLayout slot = viewActionsSlot();
        slot.removeAll();
        slot.setWidth(null);
        slot.getElement().getStyle().remove("flex-grow");
    }

    /**
     * Слот создаётся при первом обращении и встаёт в шапку сразу за кнопкой меню — там же, где
     * его ждёт вёрстка заголовка экрана.
     */
    private HorizontalLayout viewActionsSlot() {
        if (viewActionsSlot == null) {
            viewActionsSlot = uiComponents.create(HorizontalLayout.class);
            viewActionsSlot.setId("viewActionsSlot");
            viewActionsSlot.setPadding(false);
            viewActionsSlot.setSpacing(true);
            viewActionsSlot.setAlignItems(FlexComponent.Alignment.CENTER);
            header.addComponentAtIndex(header.getElement().indexOfChild(drawerToggle.getElement()) + 1,
                    viewActionsSlot);
        }
        return viewActionsSlot;
    }

//    @Subscribe("themeSwitcher.lightThemeItem.lightThemeAction")
//    public void onThemeSwitcherLightThemeItemLightThemeAction(final ActionPerformedEvent event) {
//        ThemeUtils.applyTheme("light");
//    }
//
//    @Subscribe("themeSwitcher.darkThemeItem.darkThemeAction")
//    public void onThemeSwitcherDarkThemeItemDarkThemeAction(final ActionPerformedEvent event) {
//        ThemeUtils.applyTheme("dark");
//    }

    @Install(to = "userMenu", subject = "buttonRenderer")
    private Component userMenuButtonRenderer(final UserDetails userDetails) {
        if (!(userDetails instanceof User user)) {
            return null;
        }

        String userName = generateUserName(user);

        Div content = uiComponents.create(Div.class);
        content.setClassName("user-menu-button-content");

        Avatar avatar = createAvatar(userName);

        Span name = uiComponents.create(Span.class);
        name.setText(userName);
        name.setClassName("user-menu-text");

        content.add(avatar, name);

        if (isSubstituted(user)) {
            Span subtext = uiComponents.create(Span.class);
            subtext.setText(messages.getMessage("userMenu.substituted"));
            subtext.setClassName("user-menu-subtext");

            content.add(subtext);
        }

        return content;
    }

    @Install(to = "userMenu", subject = "headerRenderer")
    private Component userMenuHeaderRenderer(final UserDetails userDetails) {
        if (!(userDetails instanceof User user)) {
            return null;
        }

        Div content = uiComponents.create(Div.class);
        content.setClassName("user-menu-header-content");

        String name = generateUserName(user);

        Avatar avatar = createAvatar(name);
        avatar.addThemeVariants(AvatarVariant.LUMO_LARGE);

        Span text = uiComponents.create(Span.class);
        text.setText(name);
        text.setClassName("user-menu-text");

        content.add(avatar, text);

        if (name.equals(user.getUsername())) {
            text.addClassNames("user-menu-text-subtext");
        } else {
            Span subtext = uiComponents.create(Span.class);
            subtext.setText(user.getUsername());
            subtext.setClassName("user-menu-subtext");

            content.add(subtext);
        }

        return content;
    }

    private Avatar createAvatar(String fullName) {
        Avatar avatar = uiComponents.create(Avatar.class);
        avatar.setName(fullName);
        avatar.getElement().setAttribute("tabindex", "-1");
        avatar.setClassName("user-menu-avatar");

        return avatar;
    }

    private String generateUserName(User user) {
        String userName = String.format("%s %s",
                        Strings.nullToEmpty(user.getFirstName()),
                        Strings.nullToEmpty(user.getLastName()))
                .trim();

        return userName.isEmpty() ? user.getUsername() : userName;
    }

    private boolean isSubstituted(User user) {
        UserDetails authenticatedUser = currentUserSubstitution.getAuthenticatedUser();
        return user != null && !authenticatedUser.getUsername().equals(user.getUsername());
    }
}
