package com.rccl.middleware.guest.impl.password.email;

public enum EmailBrandSenderEnum {
    
    ROYAL_EMAIL('R', "notifications@royalcaribbean.com"),
    CELEBRITY_EMAIL('C', "notifications@celebritycruises.com"),
    AZAMARA_EMAIL('Z', "notifications@azamaraclubcruises.com");
    
    Character brand;
    
    String emailAddress;
    
    EmailBrandSenderEnum(Character brand, String emailAddress) {
        this.brand = brand;
        this.emailAddress = emailAddress;
    }
    
    public static String getEmailAddressFromBrand(Character brand) {
        for (EmailBrandSenderEnum senderEnum : EmailBrandSenderEnum.values()) {
            if (Character.toUpperCase(brand) == senderEnum.brand) {
                return senderEnum.emailAddress;
            }
        }
        return null;
    }
}
