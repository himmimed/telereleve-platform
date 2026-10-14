package ma.ceac.telereleve.user;

/** Roles of the specification. Spring Security authorities are "ROLE_" + name(). */
public enum Role {
    ADMIN,
    OPERATOR,
    TECHNICIAN;

    public String authority() {
        return "ROLE_" + name();
    }
}
