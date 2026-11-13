package top.egon.cola.platform.tianji.contract.orchestration.automation.draft;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.time.Instant;
import java.util.stream.Stream;

/** The immutable outcome of one publication command. */
public record DraftPublicationVO(
        @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}") String draftId,
        @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER) long publishedRevision,
        @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER) long draftRevision,
        @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}") String releaseVersion,
        @NotNull @Pattern(regexp = "[0-9a-f]{64}") String sourceManifestSha256,
        @NotNull @Pattern(regexp = "[0-9a-f]{64}") String compiledManifestSha256,
        @NotNull @Pattern(regexp = "[0-9a-f]{64}") String dependencyManifestSha256,
        @NotBlank @Size(max = 128) String compilerVersion,
        @NotNull Instant publishedAt,
        @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") String commandId) {

    public DraftPublicationVO {
        if (draftId == null || !draftId.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}")
                || publishedRevision < 1 || publishedRevision >= ProcessTypedJson.V2_MAX_WIRE_INTEGER
                || draftRevision != publishedRevision + 1
                || releaseVersion == null || !releaseVersion.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                || Stream.of(sourceManifestSha256, compiledManifestSha256, dependencyManifestSha256)
                        .anyMatch(hash -> hash == null || !hash.matches("[0-9a-f]{64}"))
                || compilerVersion == null || compilerVersion.isBlank() || compilerVersion.length() > 128
                || publishedAt == null || publishedAt.getNano() % 1_000 != 0
                || commandId == null || !commandId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) {
            throw new IllegalArgumentException("Invalid automation draft publication");
        }
    }
}
