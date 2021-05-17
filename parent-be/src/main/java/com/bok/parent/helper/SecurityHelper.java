package com.bok.parent.helper;

import com.bok.parent.exception.AccountException;
import com.bok.parent.integration.dto.AccountLoginDTO;
import com.bok.parent.integration.dto.KeepAliveResponseDTO;
import com.bok.parent.integration.dto.LastAccessInfoDTO;
import com.bok.parent.integration.dto.LoginResponseDTO;
import com.bok.parent.integration.dto.LogoutResponseDTO;
import com.bok.parent.integration.dto.TokenInfoResponseDTO;
import com.bok.parent.model.AccessInfo;
import com.bok.parent.model.Account;
import com.bok.parent.model.Token;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Component
@Slf4j
public class SecurityHelper {

    @Autowired
    JWTAuthenticationHelper jwtAuthenticationHelper;

    @Autowired
    TokenHelper tokenHelper;

    @Autowired
    AuditHelper auditHelper;

    @Autowired
    AccountHelper accountHelper;

    public LoginResponseDTO login(AccountLoginDTO accountLoginDTO) {
        Preconditions.checkArgument(Objects.nonNull(accountLoginDTO.password));
        Preconditions.checkArgument(Objects.nonNull(accountLoginDTO.email));
        LoginResponseDTO response = new LoginResponseDTO();

        Account account = accountHelper.findByEmail(accountLoginDTO.email).orElseThrow(() -> new AccountException("Account not found!"));
        if (isFalse(account.getEnabled())) {
            throw new AccountException("Account has not been verified!");
        }

        Optional<Token> tokenOptional = tokenHelper.getActiveToken(account.getCredentials().getEmail());
        if (tokenOptional.isPresent()) {
            response.token = tokenOptional.get().tokenString;
        } else {
            response.token = jwtAuthenticationHelper.login(account, accountLoginDTO.password);
        }

        response.lastAccessInfo = getLastAccessInfoByAccountId(account.getId());
        log.info("User {} logged in", accountLoginDTO.email);
        return response;
    }

    public Long getAccountId(String token) {
        return jwtAuthenticationHelper.extractAccountIdFromToken(token);
    }

    public TokenInfoResponseDTO getTokenInfo(String token) {
        return tokenHelper.getTokenInfo(token);
    }

    public KeepAliveResponseDTO keepAlive(String tokenString) {
        KeepAliveResponseDTO keepAliveResponse = new KeepAliveResponseDTO();
        Token token = tokenHelper.getTokenByTokenString(tokenString);
        if (token.expiresAt.isBefore(Instant.now().plusSeconds(60))) {
            keepAliveResponse.token = tokenHelper.replaceOldToken(token).getTokenString();
        }
        return keepAliveResponse;
    }

    public LogoutResponseDTO logout(String token) {
        return new LogoutResponseDTO(tokenHelper.invalidateToken(token));
    }

    @Scheduled(cron = "0 0 * * * *")
    public void deleteExpiredToken() {
        tokenHelper.deleteExpiredTokens();
    }

    public LastAccessInfoDTO lastAccessInfo(String token) {
        Long accountId = tokenHelper.getAccountIdByTokenString(token);
        return getLastAccessInfoByAccountId(accountId);
    }

    private LastAccessInfoDTO getLastAccessInfoByAccountId(Long accountId) {
        AccessInfo accessInfo = auditHelper.findLastAccessInfo(accountId);
        LastAccessInfoDTO lastAccessInfo = new LastAccessInfoDTO();
        if (Objects.nonNull(accessInfo)) {
            lastAccessInfo.lastAccessDateTime = LocalDateTime.ofInstant(accessInfo.getTimestamp(), ZoneOffset.UTC);
            lastAccessInfo.lastAccessIP = accessInfo.getIpAddress();
        } else {
            lastAccessInfo.lastAccessDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
            lastAccessInfo.lastAccessIP = "";
        }
        return lastAccessInfo;
    }
}
