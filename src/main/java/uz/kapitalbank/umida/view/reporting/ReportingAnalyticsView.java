package uz.kapitalbank.umida.view.reporting;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import uz.kapitalbank.umida.view.main.MainView;

@Route(value = "reporting-analytics-view", layout = MainView.class)
@ViewController(id = "umida_ReportingAnalyticsView")
@ViewDescriptor(path = "reporting-analytics-view.xml")
public class ReportingAnalyticsView extends StandardView {
}