package br.com.craftpanel.tools;

import br.com.craftpanel.users.User;

import java.util.List;
import java.util.Map;

/** One operation on the Minecraft server, exposed both to MCP clients and to the panel's AI chat. */
public record ServerTool(String name, String description, Access access, List<Param> params, Handler handler) {

    /** Decides who may run a tool without further confirmation; READ never changes anything. */
    public enum Access { READ, WRITE, DESTRUCTIVE }

    public record Param(String name, String type, String description, boolean required) {
        public static Param string(String name, String description) {
            return new Param(name, "string", description, true);
        }

        public static Param optionalString(String name, String description) {
            return new Param(name, "string", description, false);
        }

        public static Param optionalInt(String name, String description) {
            return new Param(name, "integer", description, false);
        }

        public static Param optionalBool(String name, String description) {
            return new Param(name, "boolean", description, false);
        }
    }

    /** Who is calling: the tool runs with this user's own permissions. */
    public record Caller(User user, String source, String ip) {}

    public record Outcome(String text, boolean error) {}

    @FunctionalInterface
    public interface Handler {
        String call(Args args, Caller caller) throws Exception;
    }

    /** A tool call's arguments, which come from a remote client or a model and are checked here. */
    public record Args(Map<String, Object> values) {
        public String optional(String name) {
            Object value = values == null ? null : values.get(name);
            return value == null ? null : String.valueOf(value);
        }

        public String required(String name) {
            String value = optional(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing required argument: " + name);
            }
            return value;
        }

        /** Like {@link #required} but keeps blank text, for file contents. */
        public String requiredText(String name) {
            String value = optional(name);
            if (value == null) {
                throw new IllegalArgumentException("Missing required argument: " + name);
            }
            return value;
        }

        public int intValue(String name, int fallback, int min, int max) {
            Object value = values == null ? null : values.get(name);
            int parsed = fallback;
            if (value instanceof Number number) {
                parsed = number.intValue();
            } else if (value != null) {
                try {
                    parsed = Integer.parseInt(String.valueOf(value).trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Argument " + name + " must be an integer");
                }
            }
            return Math.max(min, Math.min(max, parsed));
        }

        public boolean boolValue(String name) {
            Object value = values == null ? null : values.get(name);
            return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
        }
    }
}
