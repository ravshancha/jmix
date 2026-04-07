package uz.kapitalbank.umida.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import elemental.json.JsonObject;

@Tag("org-chart")
@JsModule("./src/component/orgchart/orgchart.js")
@NpmPackage(value = "@balkangraph/orgchart.js", version = "9.0.57")
public class OrgChart extends Component implements HasSize {

    public void setData(String json) {
        getElement().callJsFunction("setData", json);
    }

    public void addNodeClickListener(NodeClickListener listener) {
        getElement().addEventListener("node-click", event -> {
            JsonObject detail = event.getEventData().getObject("event.detail");
            listener.onClick(detail);
        }).addEventData("event.detail");
    }

    public interface NodeClickListener {
        void onClick(JsonObject nodeData);
    }
}
