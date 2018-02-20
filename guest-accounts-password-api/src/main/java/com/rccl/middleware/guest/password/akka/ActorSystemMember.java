package com.rccl.middleware.guest.password.akka;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lightbend.lagom.serialization.Jsonable;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActorSystemMember implements Jsonable {
    
    private static final long serialVersionUID = 1L;
    
    String address;
    
    String status;
}
