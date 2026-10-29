package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** JWT -> provider profile. Provider ids sent by clients are never used to decide ownership. */
@Component
@RequiredArgsConstructor
public class ProviderAccess {

    private final ProviderRepository providerRepository;

    public Provider requireOwn(AuthUser me) {
        if (me == null || !me.isProvider()) {
            throw BusinessException.forbidden("This action requires a provider account");
        }
        return providerRepository.findByUserId(me.id())
                .orElseThrow(() -> BusinessException.forbidden("No provider profile for this account"));
    }

    public Optional<Provider> findOwn(AuthUser me) {
        if (me == null || !me.isProvider()) return Optional.empty();
        return providerRepository.findByUserId(me.id());
    }

    public Provider require(Long providerId) {
        return providerRepository.findById(providerId).orElseThrow(() -> BusinessException.notFound(ErrorCode.PROVIDER_NOT_FOUND));
    }
}
