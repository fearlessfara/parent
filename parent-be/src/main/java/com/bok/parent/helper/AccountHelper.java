package com.bok.parent.helper;

import com.bok.integration.EmailMessage;
import com.bok.parent.dto.RegisterAccount;
import com.bok.parent.exception.EmailAlreadyExistsException;
import com.bok.parent.message.KryptoAccountCreationMessage;
import com.bok.parent.model.Account;
import com.bok.parent.model.AccountConfirmationToken;
import com.bok.parent.model.TemporaryUserData;
import com.bok.parent.repository.AccountConfirmationTokenRepository;
import com.bok.parent.repository.AccountRepository;
import com.bok.parent.repository.TemporaryUserDataRepository;
import com.bok.parent.utils.CryptoUtils;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;


@Component
@Slf4j
public class AccountHelper {

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    AccountConfirmationTokenRepository accountConfirmationTokenRepository;

    @Autowired
    TemporaryUserDataRepository temporaryUserDataRepository;

    @Autowired
    CryptoUtils cryptoUtils;

    @Autowired
    MessageHelper messageHelper;

    public Account register(RegisterAccount registerAccount) {
        Preconditions.checkArgument(Objects.nonNull(registerAccount.password));
        Preconditions.checkArgument(Objects.nonNull(registerAccount.email));
        Preconditions.checkArgument(Objects.nonNull(registerAccount.name));
        Preconditions.checkArgument(Objects.nonNull(registerAccount.surname));

        if (accountRepository.existsByEmail(registerAccount.email)) {
            throw new EmailAlreadyExistsException("Account already registered.");
        }
        Account account = new Account();
        account.setEmail(registerAccount.email);
        account.setPassword(cryptoUtils.encryptPassword(registerAccount.password));
        account.setEnabled(false);
        account.setRole(Account.Role.USER);
        account = accountRepository.save(account);
        saveTemporaryUserData(account, registerAccount.name, registerAccount.surname, null);
        sendAccountConfirmationEmail(account);
        return account;
    }

    private void sendAccountConfirmationEmail(Account account) {
        AccountConfirmationToken token = new AccountConfirmationToken(account);

        EmailMessage emailMessage = new EmailMessage();
        emailMessage.to = account.getEmail();
        emailMessage.subject = "BOK Account Verification";
        emailMessage.text = "Click on the link to verify your BOK account: https://bok.faraone.ovh:8082/confirm?token=" + token.getConfirmationToken();

        messageHelper.send(emailMessage);
    }

    private void saveTemporaryUserData(Account account, String name, String surname, Date birthdate) {
        TemporaryUserData t = new TemporaryUserData();
        t.setAccount(account);
        t.setName(name);
        t.setSurname(surname);
        t.setBirthDate(birthdate);
    }


    public Optional<Account> findByEmail(String email) {
        return accountRepository.findByEmail(email);
    }

    public Long findIdByEmail(String email) {
        return accountRepository.findIdByEmail(email);
    }

    private void notifyServices(Account account) {
        KryptoAccountCreationMessage kryptoMessage = new KryptoAccountCreationMessage();
        kryptoMessage.accountId = account.getId();
        TemporaryUserData userData = temporaryUserDataRepository.findByAccount_Id(account.getId());
        kryptoMessage.email = account.getEmail();
        kryptoMessage.name = userData.getName();
        kryptoMessage.surname = userData.getSurname();

        messageHelper.send(kryptoMessage);

        //here bank should be notified about the creation of the user
    }

    public String confirmAccount(String accountConfirmationToken) {
        Preconditions.checkArgument(Objects.nonNull(accountConfirmationToken));
        AccountConfirmationToken token = accountConfirmationTokenRepository.findByConfirmationToken(accountConfirmationToken);
        Preconditions.checkArgument(Objects.nonNull(token));

        Account account = accountRepository.findByEmail(token.getAccount().getEmail()).orElseThrow(() -> new RuntimeException("Error while activating you account, contact customer care"));
        account.setEnabled(true);
        accountRepository.save(account);
        notifyServices(account);

        return "Your account has been confirmed, you can now login to the user area.";
    }
}
