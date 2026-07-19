package com.cpallas.expenses.storage.repo;

import com.cpallas.expenses.enums.EntitlementSubjectType;
import com.cpallas.expenses.enums.Feature;
import com.cpallas.expenses.storage.ids.EntitlementId;
import com.cpallas.expenses.storage.jpa.EntitlementJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntitlementRepo extends JpaRepository<EntitlementJpa, EntitlementId> {

    List<EntitlementJpa> findAllBySubjectTypeAndSubjectIdAndFeatureAndEnabledTrue(
            EntitlementSubjectType subjectType,
            String subjectId,
            Feature feature
    );
}
