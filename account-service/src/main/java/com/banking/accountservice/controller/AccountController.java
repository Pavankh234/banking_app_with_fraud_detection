package com.banking.accountservice.controller;


import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

import static org.springframework.data.redis.connection.ReactiveStreamCommands.AddStreamRecord.body;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Slf4j
public class AccountController {
    private final AccountService accountService;


    //list of endpoints we are adding
    //create account , delete account , get account , get balance , deduct balance
    //credit balance  ( credit receive and credit send (refund))

    @PostMapping("/create-account")
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createAccount(request));
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable String accountNumber){
        return ResponseEntity.ok(accountService.getAccount(accountNumber));
    }

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BigDecimal> getBalance(
            @PathVariable String accountNumber){
        return ResponseEntity.ok(accountService.getBalance(accountNumber));
    }

    @PutMapping("/{accountNumber}/block")
    public ResponseEntity<String> blockAccount(
            @PathVariable String accountNumber){
        return ResponseEntity.ok("Account blocked successfully");
    }

    /**
     * saga step
     * deduct balance
     * called by transaction service when a transfer is initiated
     */
    @PutMapping("/{accountNumber}/deduct/{amount}")
    public ResponseEntity<String> deductBalance(
            @PathVariable String accountNumber,
            @PathVariable BigDecimal amount){
        accountService.deductBalance(accountNumber, amount);
        return ResponseEntity.ok("Balance deducted successfully");
    }

    /**
     * saga step
     * compensate transaction endpoint
     * called by transaction service in 2 scenarios
     * 1. when a fraud is detected mid-way by transaction service
     * 2. Transaction completed -> credit receiver
     */

    @PutMapping("/{accountNumber}/credit/{amount}")
    public ResponseEntity<String> creditBalance(
            @PathVariable String accountNumber,
            @PathVariable BigDecimal amount){
        accountService.creditBalance(accountNumber, amount);
        return ResponseEntity.ok("Balance credited successfully");
    }

}
