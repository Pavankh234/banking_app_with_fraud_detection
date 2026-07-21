package com.banking.frauddetectionservice.service;

import com.banking.frauddetectionservice.client.AccountServiceClient;
import com.banking.frauddetectionservice.model.FraudCheckResult;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableFeignClients
public class FraudDetectionService {

    private final AccountServiceClient accountServiceClient;
    private static final String VERIFICATION_REQUIRED_TOPIC = "transaction.required";
    private static final String FRAUD_CHECK_CLEAN_RESULT_TOPIC = "transaction.valid";
    private final KafkaTemplate<String,Map<String ,Object>> kafkaTemplate;
    private final RedisTemplate<String,String> redisTemplate;
    @Value("${fraud.max-transaction-per-minute}")
    private int maxTransactionPerMinute;

    public void checkTransaction(@Payload Map<String ,Object> payload
    ) {
        String transactionId = (String) payload.get("transactionId");
        String accountNumber = (String) payload.get("senderAccountNumber");
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());
        String description = (String) payload.get("description");


        //fetch real balance from account service
        BigDecimal senderBalance = accountServiceClient.getBalance(accountNumber);
        log.info("Balance fetched from account service :{}",senderBalance);

        //perform suspicious check/fraud detection
        FraudCheckResult result =performFraudChecks(accountNumber,amount,senderBalance);
        if(result.isFraud())
        {
            log.info("Suspicious activity detected inaccount :{}",accountNumber);
            log.info("Please enter OTP to proceed for :{}",result.getReason());

            //publish this event to kafka topic so that transaction service can consume it and can ask for OTP ( otp fun is implemented in transaction service)
            // this otp generated event is published to Kafka again by transaction service which will be consumed by notification service and that will call verify otp  so that user can get OTP
            Map<String,Object>verificationEvent = new HashMap<>();
            verificationEvent.put("transactionID",transactionId);
            verificationEvent.put("accountNumber",accountNumber);
            verificationEvent.put("amount",amount);
            verificationEvent.put("reason",result.getReason());
            kafkaTemplate.send(VERIFICATION_REQUIRED_TOPIC,transactionId,verificationEvent);

        }
        else{
            log.info("Transaction is valid");
            Map<String,Object>transactionCleanEvent = new HashMap<>();
            transactionCleanEvent.put("transactionID",transactionId);
            transactionCleanEvent.put("isFraud",false);
            transactionCleanEvent.put("reason",null);
            kafkaTemplate.send( FRAUD_CHECK_CLEAN_RESULT_TOPIC,transactionId,transactionCleanEvent);
        }
    }

    private FraudCheckResult performFraudChecks(String accountNumber, BigDecimal amount, BigDecimal senderBalance) {

        // 3 patterns of check we will do
        // 1. Velocity check ( usually fraudsters run a script to trigger multiple transactions) so if more than x transactions in 1 minute then thats a FLAG
        // 2. if amount to withdraw is 3 or 5 times his average of his transactions
        // 3. If transaction is 90 percent of users balance


        if(isVelocityExceeded(accountNumber))
        {
            return new FraudCheckResult(true,"Velocity check failed as it exceeded limit");
        }

        if(isAmountSuspicious(accountNumber,amount))
        {
            return new FraudCheckResult(true,"Amount check failed as it exceeded limit");
        }

        if(senderBalance.compareTo(BigDecimal.ZERO)>0 && isBalanceCheckFailed(senderBalance,amount))
        {
           return new FraudCheckResult(true,"Transaction limit is exceeding more than 90 percent than balance");
        }

      return new FraudCheckResult(false,"NULL");
    }

    private boolean isVelocityExceeded(String accountNumber) {
        String key="fraud:velocity" + accountNumber;
        Long count = redisTemplate.opsForValue().increment(key, 1);
        if (count!=null && count ==1) {
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }
        log.info(" Velocity check - account :{} count :{}/{}",accountNumber,count,maxTransactionPerMinute);
        return count != null && count > maxTransactionPerMinute;
    }

    private boolean isAmountSuspicious(String accountNumber, BigDecimal amount) {
        String avgKey="fraud:avg_amount" + accountNumber;
        String avgStr = redisTemplate.opsForValue().get(avgKey);

        if(avgStr==null)
        {
            redisTemplate.opsForValue().set(avgKey,amount.toString());
            return false;
        }
        BigDecimal avg = new BigDecimal(avgStr);
        BigDecimal threshold = avg.multiply(new BigDecimal("5"));
        BigDecimal newAvg = avg.add(amount).divide(BigDecimal.valueOf(2),2, RoundingMode.HALF_UP);
        redisTemplate.opsForValue().set(avgKey,newAvg.toString());

        log.info("Amount check - amount :{} threshold :{} suspicious :{}",amount,threshold,amount.compareTo(threshold) > 0);
        return amount.compareTo(threshold) > 0;
    }

    private boolean isBalanceCheckFailed(BigDecimal senderBalance, BigDecimal amount) {
        BigDecimal maxAllowed = senderBalance.multiply(new BigDecimal("0.9"));
        return amount.compareTo(maxAllowed) > 0;
    }

}
