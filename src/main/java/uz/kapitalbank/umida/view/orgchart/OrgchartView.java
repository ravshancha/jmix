package uz.kapitalbank.umida.view.orgchart;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.router.Route;
import elemental.json.Json;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.component.OrgChart;
import uz.kapitalbank.umida.entity.Division;
import uz.kapitalbank.umida.entity.Employee;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.*;

@Route(value = "orgchart-view", layout = MainView.class)
@ViewController(id = "umida_OrgchartView")
@ViewDescriptor(path = "orgchart-view.xml")
public class OrgchartView extends StandardView {

    @Autowired
    private ObjectMapper mapper;

    @ViewComponent
    private CollectionContainer<Employee> employeesDc;
    @ViewComponent
    private CollectionLoader<Employee> employeesDl;
    @ViewComponent
    private OrgChart orgChart;

    @Subscribe
    public void onInit(final InitEvent event) {
    }

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        employeesDl.load();
    }

    @Subscribe(id = "employeesDl", target = Target.DATA_LOADER)
    public void onEmployeesDlPostLoad(final CollectionLoader.PostLoadEvent<Employee> event) {
        loadChart();
    }

    private void loadChart() {
        List<Map<String, Object>> nodes = new ArrayList<>();

        for (Employee e : employeesDc.getItems()) {

            Map<String, Object> node = new HashMap<>();

            node.put("id", e.getId().toString());
            node.put("name", String.format("%s %s", e.getLastName(), e.getFirstName()));
            node.put("title", e.getJobTitle());

            if (e.getDivision() != null && e.getDivision().getPid() != null) {
                node.put("pid", e.getDivision().getPid());
                node.put("tags", List.of("group"));
            }

            nodes.add(node);
        }

        try {
            String json = mapper.writeValueAsString(nodes);
            System.out.println(json);
            orgChart.setData(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}