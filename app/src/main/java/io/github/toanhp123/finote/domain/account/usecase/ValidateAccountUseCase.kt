package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.model.Account
import javax.inject.Inject

/** Checks an account before it is written. Throws [IllegalArgumentException] with a reason. */
class ValidateAccountUseCase
    @Inject
    constructor() {
        operator fun invoke(account: Account) {
            require(account.name.isNotBlank()) { "name must not be blank" }
            require(account.currency == "VND") { "only VND is supported" }
        }
    }
