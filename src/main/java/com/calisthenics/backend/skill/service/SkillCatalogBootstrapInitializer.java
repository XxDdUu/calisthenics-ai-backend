package com.calisthenics.backend.skill.service;

import com.calisthenics.backend.skill.repository.SkillCategoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillCatalogBootstrapInitializer {

    private final SkillCategoryRepository skillCategoryRepository;
    private final DataSource dataSource;

    @PostConstruct
    public void ensureTaxonomySeeded() {
        try {
            if (skillCategoryRepository.count() == 0) {
                String sql = new String(
                        new ClassPathResource("db/migration/V59__create_skill_progression_catalog.sql")
                                .getInputStream()
                                .readAllBytes(),
                        StandardCharsets.UTF_8
                );
                // Strip the "-- 6. Seed essential baseline exercises" INSERT INTO exercises block
                // during non-Flyway test bootstrap so existing integration tests start with an untouched exercises table.
                int startInsertEx = sql.indexOf("-- 6. Seed essential baseline exercises");
                int startSection7 = sql.indexOf("-- 7. Populate skill_exercise_links");
                String taxonomyOnlySql = (startInsertEx >= 0 && startSection7 > startInsertEx)
                        ? sql.substring(0, startInsertEx) + sql.substring(startSection7)
                        : sql;

                ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                        new ByteArrayResource(taxonomyOnlySql.getBytes(StandardCharsets.UTF_8))
                );
                populator.setContinueOnError(false);
                populator.execute(dataSource);
                log.info("Bootstrapped Skill Progression & Catalog taxonomy tables.");
            }
        } catch (Exception ex) {
            log.warn("Could not auto-bootstrap V59 skill taxonomy: {}", ex.getMessage());
        }
    }

    public void seedFullCatalogWithBaselineExercises() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V59__create_skill_progression_catalog.sql")
        );
        populator.setContinueOnError(false);
        populator.execute(dataSource);
    }
}
