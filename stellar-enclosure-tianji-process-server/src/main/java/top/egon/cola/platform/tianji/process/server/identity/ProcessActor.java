package top.egon.cola.platform.tianji.process.server.identity;

/** Created by trusted authentication code; request bodies cannot establish this identity. */
public record ProcessActor(long tenantId, SubjectType subjectType, String subjectId) {
    public enum SubjectType { USER, SERVICE }

    public ProcessActor {
        if (tenantId <= 0 || subjectType == null || subjectId == null || subjectId.isBlank()
                || subjectId.length() > 256 || !subjectId.equals(subjectId.trim())) throw invalid();
        for (int i = 0; i < subjectId.length(); i++) {
            char character = subjectId.charAt(i);
            if (character < 32 || character == 127 || Character.isLowSurrogate(character)
                    || Character.isHighSurrogate(character)
                    && (i + 1 >= subjectId.length() || !Character.isLowSurrogate(subjectId.charAt(++i)))) throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Process actor is invalid");
    }
}
