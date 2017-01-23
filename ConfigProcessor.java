import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/**
 * Process config to generate final config with parameters applied
 */
final class ConfigProcessor {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Missing config values. Provide semi-colon separated config values and env. parameters.");
            return;
        }

        Map<String, List<ParameterizedConfigEntry>> parameterizedConfigProperties = new HashMap<>();
        for (String config : args[0].split(";")) {
            String[] kv = config.split(":");
            ParameterizedConfigEntry pce = new ParameterizedConfigEntry(kv[0], kv[1]);
            List<ParameterizedConfigEntry> pces = parameterizedConfigProperties.get(pce.key);
            if (pces == null) {
                pces = new ArrayList<>();
                parameterizedConfigProperties.put(pce.key, pces);
            }

            pces.add(pce);
        }

        String[] envParamsRaw = args[1].split(";");
        Set<Parameter> envParams = Parameter.parseAllKnownParameters(envParamsRaw);

        Map<String, String> configProperties = new HashMap<>();
        parameterizedConfigProperties.forEach((k,v) -> {
            ParameterizedProperty pp = new ParameterizedProperty(v);
            configProperties.put(k, pp.getResolvedValue(envParams));
        });

        configProperties.forEach((k,v) -> System.out.println("Key: " + k + " Value: " + v));
    }

    private static class Parameter {
        private ParameterType type;
        private String value;

        private Parameter(ParameterType type, String value) {
            this.type = type;
            this.value = value;
        }

        private int getPriority() {
            return this.type.getPriority();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj == null) {
                return false;
            }

            if (!(obj instanceof Parameter)) {
                return false;
            }

            Parameter other = (Parameter)obj;
            return this.type == other.type && this.value == other.value;
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + this.type.hashCode();
            result = 31 * result + this.value.hashCode();
            return result;
        }

        private static Set<Parameter> parseAllKnownParameters(String[] rawParameters) {
            Set<Parameter> parsedParameters = new HashSet<>();
            if (rawParameters == null) {
                return parsedParameters;
            }

            for (String rawParameter : rawParameters) {
                String[] pv = rawParameter.split("=");
                if (pv.length != 2) {
                    continue;
                }

                ParameterType type = ParameterType.fromString(pv[0]);
                if (type == null) {
                    continue;
                }

                parsedParameters.add(new Parameter(type, pv[1].trim()));
            }

            return parsedParameters;
        }

        private static enum ParameterType {
            // priority is defined by arbitrary integer value. The integer values itself do not have any meaning, but
            // the ordering of them gives one Parameter higher priority over another. A parameter with larger priority
            // value has higher priority.
            OS("os", 10),
            Role("role", 1),
            Service("service", 5);

            private String typeString;
            private int priority;

            private ParameterType(String typeString, int priority) {
                this.typeString = typeString;
                this.priority = priority;
            }

            private int getPriority() {
                return this.priority;
            }

            private static ParameterType fromString(String typeString) {
                if (typeString == null) {
                    return null;
                }

                typeString = typeString.trim();
                for (ParameterType pt : ParameterType.values()) {
                    if (typeString.equalsIgnoreCase(pt.typeString)) {
                        return pt;
                    }
                }

                return null;
            }
        }
    }

    private static class ParameterizedConfigEntry {
        private final String key;
        private final Set<Parameter> parameters;
        private final String value;

        private ParameterizedConfigEntry(String parameterizedKey, String value) {
            String[] kps = parameterizedKey.split("?");
            this.key = kps[0].trim();
            if (kps.length == 1) {
                this.parameters = null;
            } else {
                String[] rawParameters = kps[1].trim().split("&");
                this.parameters = Parameter.parseAllKnownParameters(rawParameters);
            }

            this.value = value;
        }

        private boolean isCompatible(Set<Parameter> envParams) {
            if (this.parameters == null) {
                return true;
            }

            return envParams.containsAll(this.parameters);
        }

        private static ParameterizedConfigEntry pickBetterMatch(
                ParameterizedConfigEntry first,
                ParameterizedConfigEntry second) {
            // check first and second are not null
            if (first.parameters == null) {
                return second;
            }

            if (second.parameters == null) {
                return first;
            }

            if (first.parameters.size() > second.parameters.size()) {
                return first;
            }

            if (first.parameters.size() < second.parameters.size()) {
                return second;
            }

            // Both entries have same number of parameters, pick one with highest priority parameter
            int firstHighestPriority =
                first.parameters.stream().map(Parameter::getPriority).max(Comparator.naturalOrder()).get();
            int secondHighestPriority =
                second.parameters.stream().map(Parameter::getPriority).max(Comparator.naturalOrder()).get();
            return firstHighestPriority > secondHighestPriority ? first : second;
        }
    }

    private static class ParameterizedProperty {
        private List<ParameterizedConfigEntry> parameterizedConfigEntries;

        private ParameterizedProperty(Collection<ParameterizedConfigEntry> pces) {
            parameterizedConfigEntries = new ArrayList<>(pces);
        }

        private String getResolvedValue(Set<Parameter> envParams) {
            ParameterizedConfigEntry result = null;
            for (ParameterizedConfigEntry pce : this.parameterizedConfigEntries) {
                if (!pce.isCompatible(envParams)) {
                    continue;
                }

                if (result == null || result.parameters == null) {
                    result = pce;
                } else {
                    result = ParameterizedConfigEntry.pickBetterMatch(result, pce);
                }
            }

            return result == null ? null : result.value;
        }
    }
}

