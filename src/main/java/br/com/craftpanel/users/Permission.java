package br.com.craftpanel.users;

import java.util.Set;

public final class Permission {
    public static final String ALL = "*";

    public static final String SERVER_VIEW = "server.view";
    public static final String SERVER_START = "server.start";
    public static final String SERVER_STOP = "server.stop";
    public static final String SERVER_RESTART = "server.restart";
    public static final String SERVER_KILL = "server.kill";
    public static final String SERVER_CREATE = "server.create";
    public static final String SERVER_EDIT = "server.edit";
    public static final String SERVER_DELETE = "server.delete";

    public static final String CONSOLE_VIEW = "console.view";
    public static final String CONSOLE_EXECUTE = "console.execute";

    public static final String FILES_VIEW = "files.view";
    public static final String FILES_EDIT = "files.edit";
    public static final String FILES_UPLOAD = "files.upload";
    public static final String FILES_DOWNLOAD = "files.download";
    public static final String FILES_DELETE = "files.delete";

    public static final String PLUGINS_VIEW = "plugins.view";
    public static final String PLUGINS_MANAGE = "plugins.manage";

    public static final String MODRINTH_VIEW = "modrinth.view";
    public static final String MODRINTH_INSTALL = "modrinth.install";

    public static final String BACKUPS_VIEW = "backups.view";
    public static final String BACKUPS_MANAGE = "backups.manage";

    public static final String USERS_VIEW = "users.view";
    public static final String USERS_MANAGE = "users.manage";
    public static final String ROLES_MANAGE = "roles.manage";

    public static final String AUDIT_VIEW = "audit.view";
    public static final String MONITORING_VIEW = "monitoring.view";
    public static final String SETTINGS_MANAGE = "settings.manage";

    public static final Set<String> ALL_PERMISSIONS = Set.of(
            ALL,
            SERVER_VIEW, SERVER_START, SERVER_STOP, SERVER_RESTART, SERVER_KILL, SERVER_CREATE, SERVER_EDIT, SERVER_DELETE,
            CONSOLE_VIEW, CONSOLE_EXECUTE,
            FILES_VIEW, FILES_EDIT, FILES_UPLOAD, FILES_DOWNLOAD, FILES_DELETE,
            PLUGINS_VIEW, PLUGINS_MANAGE,
            MODRINTH_VIEW, MODRINTH_INSTALL,
            BACKUPS_VIEW, BACKUPS_MANAGE,
            USERS_VIEW, USERS_MANAGE, ROLES_MANAGE,
            AUDIT_VIEW, MONITORING_VIEW, SETTINGS_MANAGE
    );

    private Permission() {}
}
