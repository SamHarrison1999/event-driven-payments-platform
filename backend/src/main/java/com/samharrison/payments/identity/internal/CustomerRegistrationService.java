package com.samharrison.payments.identity.internal;

import com.samharrison.payments.identity.CustomerRegistered;
import java.time.Clock;
import java.time.Instant;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerRegistrationService {

    private static final String
        DUPLICATE_EMAIL_CONSTRAINT =
        "uq_identity_user_normalized_email";

    private final IdentityUserRepository repository;
    private final PasswordHashingService hashingService;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public CustomerRegistrationService(
        IdentityUserRepository repository,
        PasswordHashingService hashingService,
        Clock clock,
        ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.hashingService = hashingService;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CustomerRegistrationResult register(
        String rawEmail,
        String rawPassword
    ) {
        EmailAddress emailAddress =
            parseEmailAddress(rawEmail);

        if (
            repository.existsByNormalizedEmail(
                emailAddress.normalizedValue()
            )
        ) {
            throw new DuplicateEmailException();
        }

        String passwordHash =
            hashingService.hash(rawPassword);

        IdentityUser user =
            IdentityUser.registerCustomer(
                emailAddress,
                passwordHash,
                Instant.now(clock)
            );

        try {
            repository.saveAndFlush(user);
        } catch (
            DataIntegrityViolationException exception
        ) {
            if (
                causedByDuplicateEmailConstraint(
                    exception
                )
            ) {
                throw new DuplicateEmailException(
                    exception
                );
            }

            throw exception;
        }

        eventPublisher.publishEvent(
            new CustomerRegistered(
                user.id()
            )
        );

        return CustomerRegistrationResult.from(
            user
        );
    }

    private static EmailAddress parseEmailAddress(
        String rawEmail
    ) {
        try {
            return EmailAddress.of(rawEmail);
        } catch (
            IllegalArgumentException exception
        ) {
            throw new InvalidEmailAddressException(
                exception
            );
        }
    }

    private static boolean
    causedByDuplicateEmailConstraint(
        Throwable throwable
    ) {
        Throwable current = throwable;

        while (current != null) {
            if (
                current
                    instanceof
                    ConstraintViolationException
                        constraintViolation
                    && DUPLICATE_EMAIL_CONSTRAINT.equals(
                    constraintViolation
                        .getConstraintName()
                )
            ) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}