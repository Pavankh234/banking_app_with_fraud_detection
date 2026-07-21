package com.banking.transactionservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

import java.math.BigDecimal;

@FeignClient(name = "account-service", url = "${account.service.url}")
public interface AccountServiceClient {


    @PutMapping("/api/v1/accounts/{accountNumber}/deduct-balance/{amount}")
    String deductBalance(
            @PathVariable String accountNumber,
            @PathVariable BigDecimal amount
    );
}
