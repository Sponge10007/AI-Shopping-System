package com.aishop.common.security;

public record CurrentUser(
        String userId,
        String role
) {
    public static CurrentUser prototypeCustomer() {
        return new CurrentUser("u10001", "CUSTOMER");
    }

    public static CurrentUser prototypeMerchant() {
        return new CurrentUser("m10001", "MERCHANT");
    }
}

