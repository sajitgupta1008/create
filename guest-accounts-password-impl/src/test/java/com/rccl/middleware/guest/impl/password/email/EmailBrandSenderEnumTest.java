package com.rccl.middleware.guest.impl.password.email;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EmailBrandSenderEnumTest {
    
    @Test
    public void testSuccessfulRetrieveSenderByBrand() {
        assertEquals(EmailBrandSenderEnum.getEmailAddressFromBrand('c'),
                EmailBrandSenderEnum.CELEBRITY_EMAIL.emailAddress);
        assertEquals(EmailBrandSenderEnum.getEmailAddressFromBrand('r'),
                EmailBrandSenderEnum.ROYAL_EMAIL.emailAddress);
        assertEquals(EmailBrandSenderEnum.getEmailAddressFromBrand('z'),
                EmailBrandSenderEnum.AZAMARA_EMAIL.emailAddress);
        
        assertEquals(EmailBrandSenderEnum.getEmailAddressFromBrand('C'),
                EmailBrandSenderEnum.CELEBRITY_EMAIL.emailAddress);
        assertEquals(EmailBrandSenderEnum.getEmailAddressFromBrand('R'),
                EmailBrandSenderEnum.ROYAL_EMAIL.emailAddress);
        assertEquals(EmailBrandSenderEnum.getEmailAddressFromBrand('Z'),
                EmailBrandSenderEnum.AZAMARA_EMAIL.emailAddress);
    }
    
    @Test
    public void testRetrieveSenderByIncorrectBrand() {
        assertEquals("Must return null if brand is invalid.",
                EmailBrandSenderEnum.getEmailAddressFromBrand('X'), null);
    }
}
