package io.github.erickvaernet.starwars.shared;

/**
 * A parameter of the request has a value that is not accepted. It is answered with a 400.
 *
 * <p>It carries the name of the parameter and the rule it must follow, so that the answer can
 * tell the client what to fix. Both are texts written in the code: the value that was received
 * is never part of the error, so nothing sent by the client is repeated back.
 */
public class InvalidParameterException extends RuntimeException {

    private final String parameter;

    private final String requirement;

    /**
     * @param parameter   the name of the parameter, as the client sends it
     * @param requirement the rule it must follow, written to follow the name: "must be ..."
     */
    public InvalidParameterException(String parameter, String requirement) {
        super("Parameter '" + parameter + "' " + requirement + ".");
        this.parameter = parameter;
        this.requirement = requirement;
    }

    /** The name of the parameter that was rejected. */
    public String parameter() {
        return parameter;
    }

    /** The rule that the parameter must follow. */
    public String requirement() {
        return requirement;
    }

}
