package com.cpallas.expenses.config.ml;

import com.cpallas.expenses.ml.grpc.ExpenseClassifierGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExpenseMlProperties.class)
public class ExpenseMlConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "expense.ml", name = "mock-enabled", havingValue = "false", matchIfMissing = true)
    public ManagedChannel expenseMlChannel(ExpenseMlProperties properties) {
        return ManagedChannelBuilder
                .forAddress(properties.host(), properties.port())
                .usePlaintext()
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "expense.ml", name = "mock-enabled", havingValue = "false", matchIfMissing = true)
    public ExpenseClassifierGrpc.ExpenseClassifierBlockingStub expenseClassifierBlockingStub(ManagedChannel channel) {
        return ExpenseClassifierGrpc.newBlockingStub(channel);
    }
}
