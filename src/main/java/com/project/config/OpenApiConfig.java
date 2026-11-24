package com.project.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "Authorization";

    @Bean
    public OpenAPI schoolFindingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("校园失物招领智能匹配系统 API")
                        .description("School-Finding 后端接口。统一返回 {code,msg,data}，code=0 为成功。"
                                + "调试顺序：登录拿 token → Authorize 填 token → 发布/审核/匹配/认领。")
                        .version("1.0.0"))
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(SCHEME)
                                .description("直接填登录接口返回的 token，不用加 Bearer 前缀")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}
