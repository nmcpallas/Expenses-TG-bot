package com.cpallas.expenses.config.ml;

import com.cpallas.expenses.ml.grpc.ExpenseClassifierGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExpenseMlProperties.class)
public class ExpenseMlConfig {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel expenseMlChannel(ExpenseMlProperties properties) {
        return ManagedChannelBuilder
                .forAddress(properties.host(), properties.port())
                .usePlaintext()
                .build();
    }

    @Bean
    public ExpenseClassifierGrpc.ExpenseClassifierBlockingStub expenseClassifierBlockingStub(ManagedChannel channel) {
        return ExpenseClassifierGrpc.newBlockingStub(channel);
    }
}
