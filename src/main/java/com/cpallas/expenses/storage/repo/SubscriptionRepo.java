package com.cpallas.expenses.storage.repo;

import com.cpallas.expenses.storage.ids.SubscriptionId;
import com.cpallas.expenses.storage.jpa.SubscriptionJpa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepo extends JpaRepository<SubscriptionJpa, SubscriptionId> {
}
