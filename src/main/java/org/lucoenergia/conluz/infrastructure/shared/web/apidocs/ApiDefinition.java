package org.lucoenergia.conluz.infrastructure.shared.web.apidocs;


import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Component;

@OpenAPIDefinition(
        info = @Info(
                title = "Conluz API",
                version = "1.0.0",
                description = "Conluz is an API-driven application designed for the efficient management of an energy community,enabling the administration of community members and their corresponding supply points and the retrieval of consumption, production data.",
                license = @License(
                        name = "Apache-2.0 license",
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                ),
                contact = @Contact(
                        name = "Luco Energía",
                        url = "lucoenergia@gmail.com"
                )
        ),
        tags = {
                @Tag(
                        name = ApiTag.AUTHENTICATION,
                        description = "Authentication API endpoints handle actions like user login, providing secure access to the application by verifying user credentials and generating authentication tokens."
                ),
                @Tag(
                        name = ApiTag.CONFIGURATION,
                        description = "REST API endpoints for application configuration enable actions like setting preferences, adjusting parameters, and managing global settings, ensuring customizable and efficient application deployment."
                ),
                @Tag(
                        name = ApiTag.CONSUMPTION,
                        description = "REST API endpoints empower querying energy consumption data for diverse community supplies, enabling detailed analysis and optimization of resource usage."
                ),
                @Tag(
                        name = ApiTag.PRICES,
                        description = "REST API endpoints facilitate querying energy prices, providing real-time data from diverse sources for analysis, enabling informed decisions within the energy market."
                ),
                @Tag(
                        name = ApiTag.PRODUCTION,
                        description = "REST API endpoints enable querying production data from an energy community's power plant, accessing renewable energy metrics for analysis and monitoring."
                ),
                @Tag(
                        name = ApiTag.USERS,
                        description = "REST API endpoints for user management encompass actions like creating, editing, querying, disabling, and enabling users, ensuring flexible and secure access control."
                ),
                @Tag(
                        name = ApiTag.SUPPLIES,
                        description = "REST API endpoints for supply management include actions such as creating, editing, querying, and deleting supplies associated with users."
                )
        },
        servers = {
                @Server(
                        url = "https://localhost:8443",
                        description = "Local testing server"
                )
        },
        security = {
                @SecurityRequirement(
                        name = "bearerToken",
                        scopes = {
                                "ADMIN",
                                "PARTNER"
                        }
                )
        }
)
@SecurityScheme(
        name = "bearerToken",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT token authentication. Some endpoints will require roles ADMIN or PARTNER. Check the description of the endpoint for more details."

)
@Component
public class ApiDefinition {
}
