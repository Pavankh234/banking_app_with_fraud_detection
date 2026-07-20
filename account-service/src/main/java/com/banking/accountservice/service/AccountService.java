package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private static SecureRandom secureRandom = new SecureRandom();
    public AccountResponse createAccount(CreateAccountRequest request) {
        log.info("I am creating account for :{} ", request.getEmail());
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Account already exists for email {}" + request.getEmail());

        }
        //change this return type
        Account account= new Account();
        account.setAccountHolderName(request.getAccountHolderName());
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setAccountType(request.getAccountType());
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(request.getInitialDeposit());
        // generate account number is the function we are creating
        account.setAccountNumber(generateAccountNumber());

        //now we also need to set transaction limit based on type of the account
        account.setDailyTransactionLimit(
                request.getAccountType()== AccountType.SAVINGS? new BigDecimal("100000"):new BigDecimal("500000")
        );
        Account savedAccount = accountRepository.save(account);
        log.info("Account created :{} ",savedAccount.getAccountNumber());

        // we have to return a response but we have object so we will convert this object to response and send it
        return maptoResponse(savedAccount);
    }

    private AccountResponse maptoResponse( Account account)
    {
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setAccountNumber(account.getAccountNumber());
        response.setAccountHolderName(account.getAccountHolderName());
        response.setEmail(account.getEmail());
        response.setPhone(account.getPhone());
        response.setAccountType(account.getAccountType());
        response.setStatus(account.getStatus());
        response.setDailyTransactionLimit(account.getDailyTransactionLimit());
        response.setCreatedAt(account.getCreatedAt());
        return response;
    }

    //generate unique account number of 12 digits
    private String generateAccountNumber() {
        String accountNumber;
        do {
            long number = secureRandom.nextLong(1_000_000_000_000L);
            accountNumber = String.format("%012d", number);
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    //get account details when queried
    public AccountResponse getAccount (String accountNumber)
    {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(()->new RuntimeException("Account not found"));
        return maptoResponse(account);
    }

    //get account balance when queried
    public BigDecimal getBalance (String accountNumber)
    {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(()->new RuntimeException("Account not found"));
        return account.getBalance();
    }

    // to block account

    /**
     * This method gets called by Fraud Detection service via Kafka
     * @param accountNumber
     */
    public void blockAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(()->new RuntimeException("Account not found"));
        account.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account blocked successfully :{}",accountNumber);
    }

    // to deduct balance
    //called by transaction service via kafka
    public void deductBalance( String accountNumber, BigDecimal amount)
    {
        log.info("Deducting amount {} from account {}",amount,accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(()->new RuntimeException("Account not found"));
        if(account.getStatus()!=AccountStatus.ACTIVE)
        {
            throw new RuntimeException("Account is not active");
        }
        if(account.getBalance().compareTo(amount)<0)
        {
            throw new RuntimeException("Insufficient balance");
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        log.info("Amount deducted successfully from account {}",accountNumber);
    }

    //credit balance
    //called by transaction service via kafka
    public void creditBalance( String accountNumber,BigDecimal amount)
    {
        log.info("Deducting amount {} from account {}",amount,accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(()->new RuntimeException("Account not found"));
        if(account.getStatus()!=AccountStatus.ACTIVE)
        {
            throw new RuntimeException("Account is not active");
        }
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        log.info("Accountn is credited and new balance is  {}",account.getBalance());
    }


}
