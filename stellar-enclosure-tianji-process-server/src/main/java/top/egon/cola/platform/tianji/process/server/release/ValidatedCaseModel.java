package top.egon.cola.platform.tianji.process.server.release;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable validator output; construction neither authenticates a caller nor authorizes publication or execution. */
public final class ValidatedCaseModel {
    private final String definitionKey;
    private final byte[] xml;
    private final Map<String, ValidatedProcessModel.HumanTask> humanTasks;
    private final Map<String, PlanItemPolicy> planItems;
    private final Set<String> operatorRoleIds;

    public ValidatedCaseModel(String definitionKey, byte[] xml,
                       Map<String, ValidatedProcessModel.HumanTask> humanTasks,
                       Map<String, PlanItemPolicy> planItems) {
        this.definitionKey = Objects.requireNonNull(definitionKey, "definitionKey");
        this.xml = Objects.requireNonNull(xml, "xml").clone();
        this.humanTasks = Map.copyOf(Objects.requireNonNull(humanTasks, "humanTasks"));
        this.planItems = Map.copyOf(Objects.requireNonNull(planItems, "planItems"));
        var roles = new LinkedHashSet<String>();
        this.planItems.values().forEach(policy -> roles.addAll(policy.roleIds()));
        this.operatorRoleIds = Set.copyOf(roles);
    }

    public String definitionKey() { return definitionKey; }
    public byte[] xml() { return xml.clone(); }
    public Map<String, ValidatedProcessModel.HumanTask> humanTasks() { return humanTasks; }
    public Map<String, PlanItemPolicy> planItems() { return planItems; }
    public Set<String> operatorRoleIds() { return operatorRoleIds; }

    public enum Action { DISABLE, ENABLE, START, TERMINATE }

    public record PlanItemPolicy(String definitionId, Set<Action> actions, Set<String> roleIds) {
        public PlanItemPolicy {
            definitionId = Objects.requireNonNull(definitionId, "definitionId");
            actions = Set.copyOf(Objects.requireNonNull(actions, "actions"));
            roleIds = Set.copyOf(Objects.requireNonNull(roleIds, "roleIds"));
        }
    }
}
