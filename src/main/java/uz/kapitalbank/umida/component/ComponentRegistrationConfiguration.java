package uz.kapitalbank.umida.component;

import io.jmix.flowui.sys.registration.ComponentRegistration;
import io.jmix.flowui.sys.registration.ComponentRegistrationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ComponentRegistrationConfiguration {

    @Bean
    public ComponentRegistration orgChart() {
        return ComponentRegistrationBuilder.create(OrgChart.class)
                .withComponentLoader("org-chart", OrgChartLoader.class)
                .build();
    }
}
