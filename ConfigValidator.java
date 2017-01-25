import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validates config to ensure there are no ambiguous config overrides
 */
final class ConfigValidator {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Missing config values. Provide semi-colon separated config values.");
            return;
        }

        Map<String, List<ParameterCollection>> parameterizedConfigs = new HashMap<>();
        for (String config : args[0].split(Pattern.quote(";"))) {
            String[] kv = config.trim().split(Pattern.quote(":"));
            String[] kp = kv[0].trim().split(Pattern.quote("?"));
            if (kp.length != 2) {
                continue;
            }

            List<ParameterCollection> pcs = parameterizedConfigs.get(kp[0]);
            if (pcs == null) {
                pcs = new ArrayList<>();
                parameterizedConfigs.put(kp[0], pcs);
            }

            pcs.add(ParameterCollection.parse(kp[1]));
        }

        System.out.println("Parsed config");
        parameterizedConfigs.forEach((k, v) -> {
            for (ParameterCollection pc : v) {
                System.out.println(k + "?" + pc);
            }
        });

        parameterizedConfigs.forEach((k, v) -> {
            if (v.size() == 1) {
                // Single parameterized configs are not ambiguous
                return;
            }

            List<ParameterCollection> moreRequired = new ArrayList<>();
            for (int i = 0; i < v.size(); ++i) {
                for (int j = i + 1; j < v.size(); ++j) {
                    if (v.get(i).isAmbiguousTo(v.get(j))) {
                        ParameterCollection pc = ParameterCollection.getCombined(v.get(i), v.get(j));
                        if (v.contains(pc)) {
                            // disambiguating parameter collection already exists
                            continue;
                        }

                        moreRequired.add(pc);
                    }
                }
            }

            if (moreRequired.size() > 0) {
                System.out.println("Configuration is ambiguous, please add following parameter combinations to fix it.");
                for (ParameterCollection pc : moreRequired) {
                    System.out.println(k + "?" + pc);
                }
            }
        });
    }

    private static class ParameterCollection {
        private Set<Parameter> parameters;

        private static ParameterCollection parse(String parameters) {
            ParameterCollection pc = new ParameterCollection();
            pc.parameters = new HashSet<>();

            String[] pvs = parameters.split(Pattern.quote("&"));
            for (String pv : pvs) {
                String[] splitPV = pv.split(Pattern.quote("="));
                Parameter p = new Parameter();
                p.key = splitPV[0];
                p.value = splitPV[1];
                pc.parameters.add(p);
            }

            return pc;
        }

        private static ParameterCollection getCombined(ParameterCollection pc1, ParameterCollection pc2) {
            // TODO: Implement
            return pc1;
        }

        private boolean isAmbiguousTo(ParameterCollection other) {
            boolean result = false;
            for (Parameter myParam : this.parameters) {
                boolean continueNext = false;
                for (Parameter otherParam : other.parameters) {
                    if (myParam.key.equals(otherParam.key)) {
                        if (myParam.value.equals(otherParam.value)) {
                            continueNext = true;
                            break;
                        } else {
                            return false;
                        }
                    }
                }

                if (continueNext) {
                    continue;
                }

                result = true;
                break;
            }

            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj == null || !(obj instanceof ParameterCollection)) {
                return false;
            }

            ParameterCollection other = (ParameterCollection) obj;
            if (this.parameters == other.parameters) {
                return true;
            }

            if (this.parameters == null || other.parameters == null) {
                return false;
            }

            return this.parameters.containsAll(other.parameters)
                && other.parameters.containsAll(this.parameters);
        }


        @Override
        public int hashCode() {
            int result = 1;
            if (this.parameters == null) {
                return result;
            }

            return this.parameters.stream()
                .map(Parameter::hashCode)
                .reduce(result, (a, b) -> 31 * a + b);
        }

        @Override
        public String toString() {
            return String.join("&", this.parameters.stream().map(Parameter::toString).collect(Collectors.toList()));
        }

        private static class Parameter {
            private String key;
            private String value;

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }

                if (obj == null || !(obj instanceof Parameter)) {
                    return false;
                }

                Parameter other = (Parameter) obj;
                return this.key.equals(other.key) && this.value.equals(other.value);
            }

            @Override
            public int hashCode() {
                int result = 1;
                result = 31 * result + this.key.hashCode();
                result = 31 * result + this.value.hashCode();
                return result;
            }

            @Override
            public String toString() {
                return String.format("%s=%s", key, value);
            }
        }
    }
}
