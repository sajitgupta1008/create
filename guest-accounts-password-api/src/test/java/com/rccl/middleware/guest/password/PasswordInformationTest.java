package com.rccl.middleware.guest.password;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class PasswordInformationTest {
    
    @Test
    public void testGetterForPasswordReturnsClonedPasswordArray() {
        char[] password = {'p', 'a', 's', 's', 'w', 'o', 'r', 'd'};
        
        PasswordInformation pi = PasswordInformation.builder()
                .password(password)
                .build();
        
        char[] actualPassword = pi.getPassword();
        
        assertNotEquals(password, actualPassword);
        assertEquals(ArrayUtils.toString(password), ArrayUtils.toString(actualPassword));
    }
    
    @Test
    public void testGetterForPasswordAlwaysReturnsNewArrayInstance() {
        char[] password = {'p', 'a', 's', 's', 'w', 'o', 'r', 'd'};
        
        PasswordInformation pi = PasswordInformation.builder()
                .password(password)
                .build();
        
        char[] instanceOne = pi.getPassword();
        char[] instanceTwo = pi.getPassword();
        
        assertNotEquals(instanceOne, instanceTwo);
        assertEquals(ArrayUtils.toString(instanceOne), ArrayUtils.toString(instanceTwo));
    }
    
    @Test
    public void testGetterForPasswordHonorsEmptyArray() {
        PasswordInformation pi = PasswordInformation.builder()
                .password(new char[0])
                .build();
        
        assertNotNull(pi.getPassword());
    }
}
