package com.rccl.middleware.guest.impl.password;

import ch.qos.logback.classic.Logger;
import com.rccl.middleware.common.exceptions.MiddlewareTransportException;
import com.rccl.middleware.common.logging.RcclLoggerFactory;
import com.rccl.middleware.guest.password.ForgotPasswordToken;
import com.rccl.middleware.guest.password.exceptions.InvalidPasswordTokenException;
import com.rccl.middleware.saviynt.api.jwt.SaviyntDecodedToken;
import com.rccl.middleware.saviynt.api.jwt.SaviyntJWTDecoder;
import com.rccl.middleware.saviynt.api.jwt.VDSDecodedJWTToken;
import com.rccl.middleware.saviynt.api.jwt.WebShopperDecodedJWTToken;
import com.rccl.middleware.saviynt.api.requests.SaviyntUserToken;
import org.apache.commons.lang3.StringUtils;

public class GuestAccountsPasswordHelper {
    
    private static final Logger LOGGER = RcclLoggerFactory.getLogger(GuestAccountsPasswordHelper.class);
    
    /**
     * Parses the token and populates {@link SaviyntUserToken} with the values gathered from
     * the decoded JWT.
     *
     * @param request the {@link ForgotPasswordToken} from service request.
     * @return {@link SaviyntUserToken}
     * @throws InvalidPasswordTokenException if there's a mismatch with decoded token values VS request values.
     * @throws MiddlewareTransportException  if something goes wrong during the decoding process.
     */
    public static SaviyntUserToken populatePropertiesFromToken(ForgotPasswordToken request) {
        try {
            SaviyntDecodedToken decodedToken = SaviyntJWTDecoder
                    .decodeJwtToken(request.getToken(), SaviyntDecodedToken.class);
            
            if (StringUtils.isNotBlank(request.getVdsId())) {
                VDSDecodedJWTToken vdsDecodedJWTToken = new VDSDecodedJWTToken(decodedToken.getPipedValue());
                
                if (vdsDecodedJWTToken.getVdsId().equals(request.getVdsId())) {
                    return SaviyntUserToken.builder()
                            .user(vdsDecodedJWTToken.getEmail() + "|"
                                    + vdsDecodedJWTToken.getVdsId())
                            .token(request.getToken())
                            .build();
                } else {
                    throw new InvalidPasswordTokenException();
                }
            } else {
                // for WebShopper
                WebShopperDecodedJWTToken shopperDecodedJWTToken =
                        new WebShopperDecodedJWTToken(decodedToken.getPipedValue());
                
                if (shopperDecodedJWTToken.getWebshopperId().equals(request.getWebShopperId())) {
                    return SaviyntUserToken.builder()
                            .user(shopperDecodedJWTToken.getWebshopperId() + "|"
                                    + shopperDecodedJWTToken.getFirstName() + "|"
                                    + shopperDecodedJWTToken.getLastName() + "|"
                                    + shopperDecodedJWTToken.getWebshopperUsername())
                            .token(request.getToken())
                            .build();
                } else {
                    throw new InvalidPasswordTokenException();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error occurred when decoding JWT Saviynt token.", e.getMessage());
            throw new InvalidPasswordTokenException();
        }
    }
}
