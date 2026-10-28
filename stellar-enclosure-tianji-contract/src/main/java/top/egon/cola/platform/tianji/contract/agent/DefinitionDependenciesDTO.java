package top.egon.cola.platform.tianji.contract.agent;

import java.util.List;

public record DefinitionDependenciesDTO(List<DefinitionResourceRef> prompts, List<DefinitionResourceRef> skills,
                                        List<DefinitionResourceRef> tools, String modelRouteRef,
                                        String policyVersionRef) {
    public DefinitionDependenciesDTO {
        prompts = prompts == null ? List.of() : List.copyOf(prompts);
        skills = skills == null ? List.of() : List.copyOf(skills);
        tools = tools == null ? List.of() : List.copyOf(tools);
        modelRouteRef = ContractChecks.required(modelRouteRef, "modelRouteRef", 256);
        policyVersionRef = ContractChecks.required(policyVersionRef, "policyVersionRef", 256);
    }
}
