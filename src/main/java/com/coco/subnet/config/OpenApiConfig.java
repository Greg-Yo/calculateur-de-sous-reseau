package com.coco.subnet.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI subnetCalculatorOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Subnet Calculator API")
                .version("2.0")
                .description("IPv4 : calcul CIDR, découpage VLSM et plan d'adressage par VLAN. "
                        + "IPv6 : analyse d'un préfixe et découpage en sous-réseaux de même taille. "
                        + "Les erreurs suivent le format RFC 9457 (application/problem+json) avec un champ « code »."));
    }
}
