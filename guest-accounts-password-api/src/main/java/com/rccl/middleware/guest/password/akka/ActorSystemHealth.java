package com.rccl.middleware.guest.password.akka;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lightbend.lagom.serialization.Jsonable;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActorSystemHealth implements Jsonable {
    
    private static final long serialVersionUID = 1L;
    
    String actorSystemName;
    
    String selfAddress;
    
    List<ActorSystemMember> clusterMembers;
    
}
