package com.banking.accountservice.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

// we are consuming transaction completed events and fraud detection events to either block the account or credit the balance
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountEventConsumer {

    //two events consumed
    //1. Transaction completed event
    //2. Fraud detection event
    //3. Make a single group for both of them
    private final AccountService accountService;
    /**
     * consume transaction.completed event from kafka
     * credits the receiver with money
     * @param payload
     */

    // I am assuming that this payload is getting from kafka and we are using it for crediting to receiver account
    @KafkaListener(topics = "transaction.completed", groupId = "account-service")
    public void consumeTransactionCompleted(
            @Payload Map<String ,Object> payload) {
        try{
            String receiverAccount= (String) payload.get("receiverAccountNumber");
            BigDecimal amount= new BigDecimal(payload.get("amount").toString());
            accountService.creditBalance(receiverAccount,amount);
        }
        catch(Exception e)
        {
            log.error("Error while crediting balance: {}",e.getMessage());
        }
    }


    /*
     * consume fraud.detected event from kafka
     * blocks the account
     * @param payload
     */
    @KafkaListener(topics = "fraud.detected", groupId = "account-service")
    public void consumeFraudDetected(
            @Payload Map<String ,Object> payload) {
        try{
            String accountNumber= (String) payload.get("accountNumber");
            log.info("Fraud is detected and blocking the account {}",accountNumber);
            accountService.blockAccount(accountNumber);
        }
        catch(Exception e)
        {
            log.error("Error while blocking account: {}",e.getMessage());
        }
    }
}
