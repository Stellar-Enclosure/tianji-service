package top.egon.cola.platform.tianji.agent.server.security;

import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;

/**
 * Host integration must idempotently persist revocable authorization for this exact SERVICE tuple,
 * binding, linked intent and run, and reject a closed or changed fence before returning. Calls must
 * have a bounded deadline and must not retain the incoming SERVICE bearer token.
 */
@FunctionalInterface
public interface TianjiLinkedServiceAdmission {
    void ensure(TianjiAgentAccess.Authorized authorized, ServiceIdentityPrincipal principal,
                TianjiLinkedDispatchProperties.Binding binding, LinkedAgentStartCommand command, AgentRunDTO run);
}
