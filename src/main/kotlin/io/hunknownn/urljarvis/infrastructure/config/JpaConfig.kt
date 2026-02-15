package io.hunknownn.urljarvis.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaRepositories(basePackages = ["io.hunknownn.urljarvis.adapter.out.persistence.repository"])
class JpaConfig
