package com.cpallas.expenses.storage.repo;

import com.cpallas.expenses.ids.UserId;
import com.cpallas.expenses.storage.jpa.UserJpa;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepo extends JpaRepository<UserJpa, UserId> {

    Optional<UserJpa> findById(@NotNull UserId id);
}
