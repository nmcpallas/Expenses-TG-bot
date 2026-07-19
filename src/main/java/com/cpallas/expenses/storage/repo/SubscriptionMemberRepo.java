package com.cpallas.expenses.storage.repo;

import com.cpallas.expenses.storage.ids.SubscriptionMemberId;
import com.cpallas.expenses.storage.ids.UserId;
import com.cpallas.expenses.storage.jpa.SubscriptionMemberJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionMemberRepo extends JpaRepository<SubscriptionMemberJpa, SubscriptionMemberId> {

    List<SubscriptionMemberJpa> findAllByUserId(UserId userId);
}
