package net.dahanne.jmh;

import tools.jackson.databind.JsonNode;

public class Utils {

    public static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.isString() ? node.asString() : null;
    }

}
