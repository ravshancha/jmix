package uz.kapitalbank.umida.component;

import io.jmix.flowui.xml.layout.loader.AbstractComponentLoader;

public class OrgChartLoader extends AbstractComponentLoader<OrgChart> {

    @Override
    protected OrgChart createComponent() {
        return factory.create(OrgChart.class);
    }

    @Override
    public void loadComponent() {
        componentLoader().loadSizeAttributes(resultComponent, element);
    }
}
